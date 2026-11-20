export const QUERY_LIMIT = 300;
const RESULT_LIMIT = 8;

export function webQuery(body) {
  if (typeof body?.query !== 'string') return null;
  const query = body.query.trim();
  return query && query.length <= QUERY_LIMIT && !/[\u0000-\u001f\u007f]/u.test(query) ? query : null;
}

export function createWebSearch(key, fetcher = fetch) {
  if (!key?.trim()) throw new Error('Configure BRAVE_SEARCH_API_KEY');
  return async function search(query) {
    const endpoint = new URL('https://api.search.brave.com/res/v1/web/search');
    endpoint.search = new URLSearchParams({ q: query, count: RESULT_LIMIT, safesearch: 'moderate', text_decorations: 'false' });
    const response = await fetcher(endpoint, {
      headers: { Accept: 'application/json', 'X-Subscription-Token': key },
      signal: AbortSignal.timeout(8000), redirect: 'error',
    });
    if (!response.ok) throw new Error('Search provider unavailable');
    const { web } = await response.json();
    const results = [];
    const seen = new Set();
    for (const entry of Array.isArray(web?.results) ? web.results : []) {
      if (typeof entry?.url !== 'string' || entry.url.length > 2048 || typeof entry.title !== 'string') continue;
      let url;
      try { url = new URL(entry.url); } catch { continue; }
      const title = entry.title.trim().slice(0, 250);
      if (!title || url.protocol !== 'https:' || url.username || url.password || seen.has(url.href)) continue;
      seen.add(url.href);
      results.push({ url: url.href, title, snippet: typeof entry.description === 'string' ? entry.description.slice(0, 600) : '' });
      if (results.length === RESULT_LIMIT) break;
    }
    return results;
  };
}
