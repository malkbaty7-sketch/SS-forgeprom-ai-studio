import test from 'node:test';
import assert from 'node:assert/strict';
import { createJobs } from './jobs.mjs';

function fakeDb(responses = []) {
  const calls = [];
  return {
    calls,
    async query(sql, params) {
      calls.push({ sql, params });
      const next = responses.length ? responses.shift() : { rows: [], rowCount: 0 };
      return typeof next === 'function' ? next(sql, params) : next;
    }
  };
}

test('enqueue stores account-scoped job and returns id', async () => {
  const db = fakeDb([{ rows: [], rowCount: 1 }]);
  const jobs = createJobs(null, { pool: db });
  const id = await jobs.enqueue('user:alice', [{ role: 'user', content: 'hi' }]);
  assert.match(id, /^[a-f0-9-]{36}$/);
  assert.equal(db.calls.length, 1);
  assert.match(db.calls[0].sql, /INSERT INTO gateway_jobs/);
  assert.equal(db.calls[0].params[1], 'user:alice');
  assert.deepEqual(JSON.parse(db.calls[0].params[2]), [{ role: 'user', content: 'hi' }]);
});

test('status reads only the owning account', async () => {
  const db = fakeDb([{ rows: [{ id: 'j1', status: 'queued', result: null }] }]);
  const jobs = createJobs(null, { pool: db });
  const row = await jobs.status('user:alice', 'j1');
  assert.equal(row.status, 'queued');
  assert.match(db.calls[0].sql, /account_key=\$2/);
  assert.deepEqual(db.calls[0].params, ['j1', 'user:alice']);
});

test('runOne returns false when no queued job is available', async () => {
  const db = fakeDb([
    { rows: [], rowCount: 0 }, // delete expired
    { rows: [], rowCount: 0 }, // fail lost leases
    { rows: [] }               // claim
  ]);
  const jobs = createJobs(null, { pool: db });
  const ran = await jobs.runOne(async () => ({ ok: true }));
  assert.equal(ran, false);
  assert.equal(db.calls.length, 3);
});

test('runOne marks job done with result and clears messages', async () => {
  const job = { id: 'job-1', account_key: 'user:alice', messages: [{ role: 'user', content: 'hi' }] };
  const db = fakeDb([
    { rows: [], rowCount: 0 },
    { rows: [], rowCount: 0 },
    { rows: [job] },
    { rows: [], rowCount: 1 }
  ]);
  const jobs = createJobs(null, { pool: db });
  const ran = await jobs.runOne(async (messages, account) => {
    assert.deepEqual(messages, job.messages);
    assert.equal(account, 'user:alice');
    return { choices: [{ message: { content: 'ok' } }] };
  });
  assert.equal(ran, true);
  const update = db.calls[3];
  assert.match(update.sql, /SET status=\$2,messages=NULL,result=\$3::jsonb,lease_until=NULL/);
  assert.equal(update.params[0], 'job-1');
  assert.equal(update.params[1], 'done');
  assert.deepEqual(JSON.parse(update.params[2]), { choices: [{ message: { content: 'ok' } }] });
});

test('runOne marks job failed when generation throws or returns null', async () => {
  const job = { id: 'job-2', account_key: 'user:alice', messages: [] };
  const db = fakeDb([
    { rows: [], rowCount: 0 },
    { rows: [], rowCount: 0 },
    { rows: [job] },
    { rows: [], rowCount: 1 }
  ]);
  const jobs = createJobs(null, { pool: db });
  const ran = await jobs.runOne(async () => { throw new Error('upstream'); });
  assert.equal(ran, true);
  const update = db.calls[3];
  assert.equal(update.params[1], 'failed');
  assert.equal(update.params[2], 'null');
});

test('runOne reaps expired and lost-lease jobs before claiming', async () => {
  const db = fakeDb([
    { rows: [], rowCount: 2 },
    { rows: [], rowCount: 1 },
    { rows: [] }
  ]);
  const jobs = createJobs(null, { pool: db });
  await jobs.runOne(async () => ({ ok: true }));
  assert.match(db.calls[0].sql, /DELETE FROM gateway_jobs WHERE expires_at <= now\(\)/);
  assert.match(db.calls[1].sql, /UPDATE gateway_jobs SET status='failed',messages=NULL,lease_until=NULL WHERE status='running' AND lease_until<=now\(\)/);
  assert.match(db.calls[2].sql, /FOR UPDATE SKIP LOCKED/);
});
