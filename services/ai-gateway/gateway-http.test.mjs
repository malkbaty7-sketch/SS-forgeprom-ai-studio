import assert from 'node:assert/strict';
import { once } from 'node:events';
import test from 'node:test';
import { createAccess, digest } from './access.mjs';
import { createBilling } from './billing.mjs';
import { createGateway } from './gateway-http.mjs';
import { createWebSearch } from './web-search.mjs';

const secrets = { admin: 'admin-test-token'.repeat(3), alice: 'alice-test-token', bob: 'bob-test-token' };
const authenticate = createAccess(JSON.stringify(['alice', 'bob'].map(id => ({ id, hash: digest(secrets[id]), plan: 'free' }))), secrets.admin);

async function client(t, options = {}) {
  const server = createGateway({ authenticate, takeQuota: async () => 0, ...options }).listen(0, '127.0.0.1');
  t.after(() => new Promise(resolve => server.close(resolve)));
  await once(server, 'listening');
  return async function request(method, path, user, body) {
    const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, {
      method, headers: { 'Content-Type': 'application/json', ...(user && { Authorization: `Bearer ${secrets[user]}` }) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    assert.equal(response.headers.get('cache-control'), 'no-store');
    return { status: response.status, body: await response.json(), retryAfter: response.headers.get('retry-after') };
  };
}

test('search HTTP endpoint enforces authentication, input limits, quota, and disabled state', async t => {
  let calls = 0;
  let blocked = false;
  let fail = false;
  const path = '/v1/web/search';
  const webSearch = createWebSearch('brave-test', async (url, options) => {
    calls++;
    assert.equal(url.searchParams.get('q'), 'مونتريال');
    assert.equal(options.headers['X-Subscription-Token'], 'brave-test');
    if (fail) return { ok: false };
    return { ok: true, json: async () => ({ web: { results: [{ url: 'https://example.com/', title: 'Montréal', description: 'Guide' }] } }) };
  });
  const request = await client(t, { webSearch, takeQuota: async () => blocked ? 12 : 0 });
  assert.equal((await request('POST', path, null, { query: 'مونتريال' })).status, 401);
  assert.equal((await request('GET', path, 'alice')).status, 404);
  assert.equal((await request('POST', path, 'alice', { query: ' ' })).status, 400);
  assert.equal((await request('POST', path, 'alice', { query: 'x'.repeat(5000) })).status, 413);
  blocked = true;
  const limited = await request('POST', path, 'alice', { query: 'مونتريال' });
  assert.equal(limited.status, 429);
  assert.equal(limited.retryAfter, '12');
  assert.equal(calls, 0);
  blocked = false;
  const ok = await request('POST', path, 'alice', { query: '  مونتريال  ', editor: 'not forwarded' });
  assert.deepEqual(ok.body, { results: [{ url: 'https://example.com/', title: 'Montréal', snippet: 'Guide' }] });
  fail = true;
  assert.equal((await request('POST', path, 'alice', { query: 'مونتريال' })).status, 503);
  const disabled = await client(t);
  assert.equal((await disabled('POST', path, 'alice', { query: 'مونتريال' })).status, 404);
});

test('billing HTTP endpoint restricts issuance, isolates accounts, and reads updated payment status', async t => {
  const path = '/v1/billing/invoices';
  const input = { reference: '7da74cd1-ead8-4a35-85b9-72c1b749d6b3', accountId: 'alice', amount: 1250, currency: 'cad', description: 'Test invoice' };
  let record;
  let status = 'open';
  const writes = [];
  const pool = { async query(sql, args) {
    if (sql.startsWith('INSERT')) record ??= { customer: args[1], amount: args[2], currency: args[3], description: args[4], retryable: true };
    else if (sql.startsWith('SELECT')) return { rows: [record] };
    else if (sql.startsWith('UPDATE')) record.invoice_id = args[1];
    else assert.fail(sql);
  } };
  const stripe = async (rawUrl, options) => {
    const url = new URL(rawUrl);
    assert.equal(url.origin, 'https://api.stripe.com');
    if (options.method === 'POST') writes.push(url.pathname);
    const invoice = { id: 'in_alice', status, currency: 'cad', amount_due: 1250, amount_paid: status === 'paid' ? 1250 : 0,
      metadata: { forgeprom_reference: input.reference }, hosted_invoice_url: 'https://invoice.stripe.com/i/test' };
    return { ok: true, json: async () => url.searchParams.has('customer')
      ? { data: url.searchParams.get('customer') === 'cus_alice' ? [invoice] : [] } : invoice };
  };
  const billing = createBilling('unused', 'stripe-test', '{"alice":"cus_alice","bob":"cus_bob"}', { pool, fetcher: stripe });
  const request = await client(t, { billing });
  assert.equal((await request('GET', path, null)).status, 401);
  assert.equal((await request('POST', path, 'alice', input)).status, 403);
  assert.equal((await request('POST', path, 'admin', { ...input, amount: -10 })).status, 400);
  assert.deepEqual(writes, []);
  const issued = await request('POST', path, 'admin', input);
  assert.equal(issued.status, 200);
  assert.equal(issued.body.status, 'open');
  assert.equal(issued.body.paymentUrl, 'https://invoice.stripe.com/i/test');
  assert.equal((await request('POST', path, 'admin', input)).body.id, issued.body.id);
  assert.equal((await request('POST', path, 'admin', { ...input, amount: 1500 })).status, 409);
  assert.deepEqual(writes, ['/v1/invoices', '/v1/invoiceitems', '/v1/invoices/in_alice/finalize']);
  assert.deepEqual((await request('GET', path, 'bob')).body.invoices, []);
  status = 'paid';
  const [paid] = (await request('GET', path, 'alice')).body.invoices;
  assert.equal(paid.status, 'paid');
  assert.equal(paid.amountPaid, 1250);
});
