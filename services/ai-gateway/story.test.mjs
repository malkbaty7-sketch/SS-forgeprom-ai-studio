import test from 'node:test';
import assert from 'node:assert/strict';
import { splitChapter, embeddingVectors } from './story.mjs';

test('chunks overlap without splitting Unicode code points', () => {
  const chunks = splitChapter('س'.repeat(849) + '🎬' + 'ت'.repeat(200));
  assert.deepEqual(chunks.map(chunk => [...chunk].length), [1000, 200]);
  assert.deepEqual([...chunks[0]].slice(850), [...chunks[1]].slice(0, 150));
});

test('validates embedding dimensions and rejects duplicate indices', () => {
  const embedding = Array(1536).fill(0.2);
  assert.equal(embeddingVectors([{ index: 0, embedding }], 1).length, 1);
  assert.throws(() => embeddingVectors([{ index: 0, embedding: [1] }], 1));
  assert.throws(() => embeddingVectors([{ index: 0, embedding }, { index: 0, embedding }], 2));
});
