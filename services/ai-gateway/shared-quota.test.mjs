import test from 'node:test';
import assert from 'node:assert/strict';
import { takeSlot, TAKE_SLOT } from './shared-quota.mjs';

test('shared quota passes account key and limit as parameters', async () => {
  for (const [rowCount, expected] of [[1, 0], [0, 60]]) {
    const db = { query: async (sql, values) => {
      assert.equal(sql, TAKE_SLOT);
      assert.deepEqual(values, ['user:demo', 10]);
      return { rowCount };
    } };
    assert.equal(await takeSlot(db, { key: 'user:demo', limit: 10 }), expected);
  }
});
