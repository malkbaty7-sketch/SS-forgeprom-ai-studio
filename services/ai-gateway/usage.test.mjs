import test from 'node:test';
import assert from 'node:assert/strict';
import { meteredGeneration, tokenCounts } from './usage.mjs';

test('accepts only valid provider token counts', () => {
  assert.deepEqual(tokenCounts({ usage: { prompt_tokens: 3, completion_tokens: 0 } }), [3, 0]);
  assert.equal(tokenCounts({ usage: { prompt_tokens: -1, completion_tokens: 2 } }), null);
  assert.equal(tokenCounts({}), null);
});

test('records successful generation for its account without changing the response', async () => {
  const response = { usage: { prompt_tokens: 3, completion_tokens: 2 } };
  let calls = 0;
  const usage = { record: async (...args) => { calls++; assert.deepEqual(args, ['user:a', 'model', response]); } };
  assert.equal(await meteredGeneration(async () => response, usage, 'user:a', 'model', []), response);
  assert.equal(calls, 1);
});
