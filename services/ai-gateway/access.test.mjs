import test from 'node:test';
import assert from 'node:assert/strict';
import { digest, createAccess, consume } from './access.mjs';

test('accounts have isolated quotas, and only admin can access metrics', () => {
  const admin = 'a'.repeat(32);
  const authenticate = createAccess(JSON.stringify([
    { id: 'one', hash: digest('b'.repeat(32)), plan: 'free' },
    { id: 'two', hash: digest('c'.repeat(32)), plan: 'premium' }
  ]), admin);
  const free = authenticate(`Bearer ${'b'.repeat(32)}`);
  const premium = authenticate(`Bearer ${'c'.repeat(32)}`);
  assert.equal(authenticate('Bearer wrong'), null);
  assert.equal(authenticate(`Bearer ${admin}`).admin, true);
  assert.equal(free.admin, false);
  for (let i = 0; i < 10; i++) assert.equal(consume(free, 1000), 0);
  assert.equal(consume(free, 1000), 59);
  assert.equal(consume(premium, 1000), 0);
  assert.equal(consume(free, 60_000), 0);
});
