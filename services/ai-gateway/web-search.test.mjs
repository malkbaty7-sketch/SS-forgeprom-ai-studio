import test from 'node:test';
import assert from 'node:assert/strict';
import { createWebSearch, webQuery } from './web-search.mjs';

test('web search accepts a bounded query and rejects blank or control characters', () => {
  assert.equal(webQuery({ query: '  مونتريال  ' }), 'مونتريال');
  assert.equal(webQuery({ query: 'x'.repeat(300) }).length, 300);
  for (const query of ['', ' \n ', 42, 'x'.repeat(301), 'a\u0000b', 'a\nb']) {
    assert.equal(webQuery({ query }), null);
  }
});

test('Brave request sends only the search query and returns bounded, unique HTTPS links', async () => {
  const query = 'مونتريال & count=99';
  const search = createWebSearch('test-key', async (url, options) => {
    assert.equal(url.origin, 'https://api.search.brave.com');
    assert.equal(url.pathname, '/res/v1/web/search');
    assert.equal(url.searchParams.get('q'), query);
    assert.equal(url.searchParams.get('count'), '8');
    assert.equal(options.headers['X-Subscription-Token'], 'test-key');
    assert.equal(options.redirect, 'error');
    return { ok: true, json: async () => ({ web: { results: [
      null, { title: 'Invalid', url: 'javascript:alert(1)' },
      { title: 'Credentials', url: 'https://user:pass@example.com/' },
      { title: 'Plain HTTP', url: 'http://example.com/' },
      { title: 'Article', url: 'https://example.com', description: 'x'.repeat(700) },
      { title: 'Duplicate', url: 'https://example.com/' },
      ...Array.from({ length: 12 }, (_, i) => ({ title: `Article ${i}`, url: `https://example.com/${i}` })),
    ] } }) };
  });
  const results = await search(query);
  assert.equal(results.length, 8);
  assert.deepEqual(results[0], { title: 'Article', url: 'https://example.com/', snippet: 'x'.repeat(600) });
  assert.equal(results[1].url, 'https://example.com/0');
});

test('missing Brave configuration or a provider failure does not produce fake results', async () => {
  assert.throws(() => createWebSearch(' '), /BRAVE_SEARCH_API_KEY/);
  const search = createWebSearch('test-key', async () => ({ ok: false }));
  await assert.rejects(search('test'), /provider unavailable/);
});
