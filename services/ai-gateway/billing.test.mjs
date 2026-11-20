import test from 'node:test';
import assert from 'node:assert/strict';
import { BillingError, createBilling, invoiceInput } from './billing.mjs';

const valid = {
  reference: '7da74cd1-ead8-4a35-85b9-72c1b749d6b3', accountId: 'alice',
  amount: 1250, currency: 'cad', description: '  Studio invoice  ',
};

test('validates explicit minor-unit amount and normalizes description', () => {
  assert.deepEqual(invoiceInput(valid), { ...valid, description: 'Studio invoice' });
  for (const change of [{ amount: 12.5 }, { amount: -1250 }, { amount: '1250' },
    { currency: 'jpy' }, { reference: '../other' }, { description: '  ' }]) {
    assert.throws(() => invoiceInput({ ...valid, ...change }), error => error instanceof BillingError && error.status === 400);
  }
});

test('billing customers must be unique per gateway account', () => {
  assert.throws(() => createBilling('unused', 'test-secret', '{"alice":"cus_a","bob":"cus_a"}'), /shared/);
});

test('retry after interruption uses the same Stripe keys and completed issuance is read-only', async () => {
  let row;
  const pool = { async query(sql, args) {
    if (sql.startsWith('INSERT')) {
      row ??= { customer: args[1], amount: args[2], currency: args[3], description: args[4], retryable: true };
    } else if (sql.startsWith('SELECT')) return { rows: [row] };
    else if (sql.startsWith('UPDATE')) row.invoice_id = args[1];
    else assert.fail(sql);
  } };
  let interrupt = true;
  const requests = [];
  const fetcher = async (url, options) => {
    requests.push([url, options.headers['Idempotency-Key']]);
    if (url.endsWith('/invoiceitems') && interrupt) {
      interrupt = false;
      throw new Error('response lost');
    }
    const listing = url.includes('?customer=');
    const invoice = { id: 'in_example', status: 'open', metadata: { forgeprom_reference: valid.reference } };
    return { ok: true, json: async () => listing ? { data: [invoice] } : invoice };
  };
  const billing = createBilling('unused', 'test-secret', '{"alice":"cus_a","bob":"cus_b"}', { pool, fetcher });
  await assert.rejects(billing.issue(valid), /response lost/);
  assert.equal((await billing.issue(valid)).id, 'in_example');
  assert.deepEqual(requests.slice(0, 2), requests.slice(2, 4));
  assert.match(requests[4][0], /\/finalize$/);
  await billing.issue(valid);
  assert.equal(requests.at(-1)[1], undefined);
  await assert.rejects(billing.issue({ ...valid, amount: 1500 }), { status: 409 });
  await billing.list('user:bob');
  assert.match(requests.at(-1)[0], /customer=cus_b&/);
  assert.deepEqual(await billing.list('user:unmapped'), []);
});
