import { Pool } from 'pg';

export class BillingError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

const customerPattern = /^cus_[A-Za-z0-9]+$/;
const referencePattern = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/;

export function invoiceInput(input) {
  const { reference, accountId, amount, currency, description } = input ?? {};
  if (typeof reference !== 'string' || !referencePattern.test(reference) ||
      typeof accountId !== 'string' || !accountId || !Number.isInteger(amount) || amount < 50 || amount > 99999999 ||
      !['cad', 'usd', 'eur'].includes(currency) || typeof description !== 'string' ||
      !description.trim() || description.length > 250 || description.includes('\0')) {
    throw new BillingError(400, 'Expected reference UUID, accountId, amount in cents, currency (cad/usd/eur), and description');
  }
  return { reference, accountId, amount, currency, description: description.trim() };
}

function publicInvoice(invoice) {
  return {
    id: invoice.id, number: invoice.number, status: invoice.status, currency: invoice.currency,
    amountDue: invoice.amount_due, amountPaid: invoice.amount_paid,
    paymentUrl: invoice.hosted_invoice_url, pdfUrl: invoice.invoice_pdf,
  };
}

export function createBilling(database, secret, rawCustomers, { pool, fetcher = fetch } = {}) {
  if (!secret) throw new Error('Configure STRIPE_SECRET_KEY');
  const mapping = JSON.parse(rawCustomers ?? 'null');
  if (!mapping || typeof mapping !== 'object' || Array.isArray(mapping)) throw new Error('Configure STRIPE_CUSTOMERS');
  const customers = new Map(Object.entries(mapping).map(([id, customer]) => [`user:${id}`, customer]));
  if (!customers.size || [...customers.values()].some(c => typeof c !== 'string' || !customerPattern.test(c)) ||
      new Set(customers.values()).size !== customers.size) throw new Error('Invalid or shared Stripe customer mapping');
  const db = pool ?? new Pool({ connectionString: database });
  async function stripe(path, fields, idempotencyKey) {
    const response = await fetcher(`https://api.stripe.com/v1/${path}`, {
      method: fields ? 'POST' : 'GET',
      headers: {
        Authorization: `Bearer ${secret}`, 'Stripe-Version': '2024-06-20',
        ...(fields ? { 'Content-Type': 'application/x-www-form-urlencoded', 'Idempotency-Key': idempotencyKey } : {}),
      },
      ...(fields ? { body: new URLSearchParams(fields) } : {}),
      redirect: 'error',
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok) throw new Error(`Stripe request failed (${response.status})`);
    return response.json();
  }
  return {
    async list(accountKey) {
      const customer = customers.get(accountKey);
      if (!customer) return [];
      const { data } = await stripe(`invoices?customer=${customer}&limit=25`);
      return data.filter(invoice => invoice.status !== 'draft' && invoice.metadata?.forgeprom_reference).map(publicInvoice);
    },
    async issue(input) {
      const { reference, accountId, amount, currency, description } = invoiceInput(input);
      const customer = customers.get(`user:${accountId}`);
      if (!customer) throw new BillingError(404, 'Billing account not found');
      await db.query(`INSERT INTO gateway_invoice_requests(reference, customer, amount, currency, description)
        VALUES($1,$2,$3,$4,$5) ON CONFLICT DO NOTHING`, [reference, customer, amount, currency, description]);
      const { rows: [record] } = await db.query(`SELECT *, created_at > now() - interval '23 hours' AS retryable
        FROM gateway_invoice_requests WHERE reference=$1`, [reference]);
      if (record.customer !== customer || record.amount !== amount || record.currency !== currency || record.description !== description) {
        throw new BillingError(409, 'Invoice reference is already reserved for a different request');
      }
      if (record.invoice_id) return publicInvoice(await stripe(`invoices/${record.invoice_id}`));
      // Stripe retains idempotency keys for at least 24h. Never replay an unresolved issuance after that window.
      if (!record.retryable) throw new BillingError(409, 'Unresolved invoice; reconcile in Stripe before issuing another');
      const key = `forgeprom-invoice:${reference}`;
      const invoice = await stripe('invoices', {
        customer, currency, collection_method: 'send_invoice', days_until_due: '30', auto_advance: 'false',
        pending_invoice_items_behavior: 'exclude', 'payment_settings[payment_method_types][]': 'card',
        'metadata[forgeprom_reference]': reference,
      }, `${key}:create`);
      await stripe('invoiceitems', { customer, invoice: invoice.id, amount, currency, description }, `${key}:item`);
      const finalized = await stripe(`invoices/${invoice.id}/finalize`, { auto_advance: 'false' }, `${key}:finalize`);
      await db.query('UPDATE gateway_invoice_requests SET invoice_id=$2 WHERE reference=$1', [reference, invoice.id]);
      return publicInvoice(finalized);
    },
  };
}
