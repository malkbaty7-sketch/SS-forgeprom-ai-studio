import assert from 'node:assert/strict';
import { once } from 'node:events';
import test from 'node:test';
import { createAccess, digest } from './access.mjs';
import { createGateway } from './gateway-http.mjs';
import { shutdown } from './lifecycle.mjs';

const secrets = { alice: 'alice-test-token' };
const authenticate = createAccess(
  JSON.stringify([{ id: 'alice', hash: digest(secrets.alice), plan: 'free' }])
);

async function start(t, options = {}) {
  const logs = [];
  const logger = { info: line => logs.push(JSON.parse(line)), error() {} };
  const server = createGateway({ authenticate, takeQuota: async () => 0, logger, ...options })
    .listen(0, '127.0.0.1');
  t.after(() => new Promise(resolve => server.close(resolve)));
  await once(server, 'listening');
  const base = `http://127.0.0.1:${server.address().port}`;
  return { logs, server, base };
}

test('health is public and readiness reports only check names', async t => {
  const failing = await start(t, {
    readiness: [
      { name: 'provider', check: async () => true },
      { name: 'quota_database', check: async () => { throw new Error('postgres://secret@internal'); } }
    ]
  });
  const health = await fetch(`${failing.base}/health`);
  assert.equal(health.status, 200);
  assert.deepEqual(await health.json(), { ok: true });

  const ready = await fetch(`${failing.base}/readyz`);
  assert.equal(ready.status, 503);
  const body = await ready.json();
  assert.deepEqual(body, { ready: false, checks: { provider: 'ok', quota_database: 'failed' } });
  assert.ok(!JSON.stringify(body).includes('postgres'));

  const healthy = await start(t, { readiness: [{ name: 'provider', check: () => true }] });
  const ok = await fetch(`${healthy.base}/readyz`);
  assert.equal(ok.status, 200);
  assert.deepEqual(await ok.json(), { ready: true, checks: { provider: 'ok' } });
});

test('readiness without probes is ready and stays unauthenticated', async t => {
  const { base } = await start(t);
  const response = await fetch(`${base}/readyz`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ready: true, checks: {} });
});

test('request ids are echoed when valid and generated otherwise', async t => {
  const { base } = await start(t);
  const echoed = await fetch(`${base}/health`, { headers: { 'X-Request-Id': 'deploy-check-42' } });
  assert.equal(echoed.headers.get('x-request-id'), 'deploy-check-42');

  const invalid = await fetch(`${base}/health`, { headers: { 'X-Request-Id': 'bad id with spaces' } });
  assert.match(invalid.headers.get('x-request-id'), /^[a-f0-9-]{36}$/);

  const unauthorized = await fetch(`${base}/metrics`);
  assert.equal(unauthorized.status, 401);
  assert.match(unauthorized.headers.get('x-request-id'), /^[a-f0-9-]{36}$/);
});

test('structured logs carry request id, status and no credentials', async t => {
  const { base, logs } = await start(t);
  const response = await fetch(`${base}/metrics`, {
    headers: { Authorization: `Bearer ${secrets.alice}` }
  });
  assert.equal(response.status, 403);
  await new Promise(resolve => setTimeout(resolve, 30));
  const entry = logs.find(line => line.event === 'request');
  assert.ok(entry);
  assert.equal(entry.status, 403);
  assert.equal(entry.method, 'GET');
  assert.equal(entry.path, '/metrics');
  assert.equal(entry.account, 'user:alice');
  assert.equal(typeof entry.durationMs, 'number');
  assert.match(entry.requestId, /^[a-f0-9-]{36}$/);
  const serialized = JSON.stringify(logs);
  assert.ok(!serialized.includes(secrets.alice));
  assert.ok(!serialized.includes('Authorization'));
});

test('shutdown stops accepting work and exits cleanly', () => {
  const calls = [];
  const fake = {
    close(callback) { calls.push('close'); callback(); },
    closeIdleConnections() { calls.push('idle'); }
  };
  const exits = [];
  const handler = shutdown(fake, {
    signals: ['SIGUSR2'], timeoutMs: 1000, exit: code => exits.push(code),
    logger: { info() {}, error() {} }
  });
  handler('SIGUSR2');
  handler('SIGUSR2');
  process.removeListener('SIGUSR2', handler);
  assert.deepEqual(calls, ['close', 'idle']);
  assert.deepEqual(exits, [0]);
});

test('shutdown times out instead of hanging forever', async () => {
  const fake = { close() {}, closeIdleConnections() {} };
  const exits = [];
  const handler = shutdown(fake, {
    signals: ['SIGUSR1'], timeoutMs: 5, exit: code => exits.push(code),
    logger: { info() {}, error() {} }
  });
  handler('SIGUSR1');
  await new Promise(resolve => setTimeout(resolve, 30));
  process.removeListener('SIGUSR1', handler);
  assert.deepEqual(exits, [1]);
});
