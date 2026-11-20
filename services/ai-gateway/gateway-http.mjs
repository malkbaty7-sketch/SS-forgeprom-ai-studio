import { createServer } from 'node:http';
import { generate } from './provider.mjs';
import { BillingError } from './billing.mjs';
import { webQuery } from './web-search.mjs';
import { meteredGeneration } from './usage.mjs';

export function createGateway({ authenticate, takeQuota, jobs, usage, story, storyModule, billing, webSearch, openAiModel }) {
  let active = 0;
  const metrics = { accepted: 0, upstreamFailures: 0, completed: 0, upstreamLatencyMs: 0 };

  function send(res, status, payload, headers = {}) {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers });
    res.end(JSON.stringify(payload));
  }

  return createServer(async (req, res) => {
    if (req.method === 'GET' && req.url === '/health') return send(res, 200, { ok: true });
    const account = authenticate(req.headers.authorization);
    if (!account) return send(res, 401, { error: 'Unauthorized' });
    if (req.method === 'GET' && req.url === '/metrics') {
      return account.admin ? send(res, 200, { ...metrics, active }) : send(res, 403, { error: 'Forbidden' });
    }
    if (usage && req.method === 'GET' && req.url === '/v1/usage') {
      try { return send(res, 200, { days: await usage.recent(account.key) }); }
      catch { return send(res, 503, { error: 'Usage service unavailable' }); }
    }
    if (req.url === '/v1/web/search') {
      if (!webSearch || req.method !== 'POST') return send(res, 404, { error: 'Not found' });
      if (active >= 4) return send(res, 429, { error: 'Gateway busy' }, { 'Retry-After': '2' });
      active++;
      try {
        const retryAfter = await takeQuota(account);
        if (retryAfter) return send(res, 429, { error: 'Account limit reached' }, { 'Retry-After': String(retryAfter) });
        const chunks = [];
        let length = 0;
        for await (const chunk of req.iterator({ destroyOnReturn: false })) {
          length += chunk.length;
          if (length > 4096) return send(res, 413, { error: 'Body too large' }, { Connection: 'close' });
          chunks.push(chunk);
        }
        let query;
        try { query = webQuery(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
        catch { return send(res, 400, { error: 'Invalid JSON' }); }
        if (!query) return send(res, 400, { error: 'Expected a query up to 300 UTF-16 units without control characters' });
        return send(res, 200, { results: await webSearch(query) });
      } catch {
        return send(res, 503, { error: 'Web search unavailable' });
      } finally { active--; }
    }
    if (billing && req.url === '/v1/billing/invoices' && ['GET', 'POST'].includes(req.method)) {
      if (req.method === 'POST' && !account.admin) return send(res, 403, { error: 'Forbidden' });
      if (active >= 4) return send(res, 429, { error: 'Gateway busy' }, { 'Retry-After': '2' });
      active++;
      try {
        const retryAfter = await takeQuota(account);
        if (retryAfter) return send(res, 429, { error: 'Account limit reached' }, { 'Retry-After': String(retryAfter) });
        if (req.method === 'GET') return send(res, 200, { invoices: await billing.list(account.key) });
        const chunks = [];
        let length = 0;
        for await (const chunk of req.iterator({ destroyOnReturn: false })) {
          length += chunk.length;
          if (length > 8192) return send(res, 413, { error: 'Body too large' }, { Connection: 'close' });
          chunks.push(chunk);
        }
        let input;
        try { input = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
        catch { return send(res, 400, { error: 'Invalid JSON' }); }
        return send(res, 200, await billing.issue(input));
      } catch (error) {
        if (error instanceof BillingError) return send(res, error.status, { error: error.message });
        return send(res, 503, { error: 'Billing unavailable; retry issuance with the same reference' });
      } finally { active--; }
    }
    const jobId = /^\/v1\/jobs\/([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})$/.exec(req.url)?.[1];
    if (jobs && req.method === 'GET' && jobId) {
      try {
        const job = await jobs.status(account.key, jobId);
        return job ? send(res, 200, job) : send(res, 404, { error: 'Not found' });
      } catch { return send(res, 503, { error: 'Job service unavailable' }); }
    }
    const book = story && /^\/v1\/story\/([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})(?:\/(chapters|search))?$/.exec(req.url);
    const storyAction = book && ({ 'POST:chapters': 'index', 'POST:search': 'search', 'DELETE:': 'remove' })[`${req.method}:${book[2] ?? ''}`];
    const enqueue = jobs && req.url === '/v1/jobs';
    if (!storyAction && (req.method !== 'POST' || (!enqueue && req.url !== '/v1/chat/completions'))) return send(res, 404, { error: 'Not found' });
    if (active >= 4) return send(res, 429, { error: 'Gateway busy' }, { 'Retry-After': '2' });
    active++;
    try {
      let retryAfter;
      try { retryAfter = await takeQuota(account); }
      catch { return send(res, 503, { error: 'Quota service unavailable' }); }
      if (retryAfter) return send(res, 429, { error: 'Account limit reached' }, { 'Retry-After': String(retryAfter) });
      if (storyAction === 'remove') {
        try { await story.remove(account.key, book[1]); return send(res, 200, { deleted: true }); }
        catch { return send(res, 503, { error: 'Story index unavailable' }); }
      }
      let length = 0;
      const chunks = [];
      for await (const chunk of req.iterator({ destroyOnReturn: false })) {
        length += chunk.length;
        if (length > 64 * 1024) {
          send(res, 413, { error: 'Body too large' }, { Connection: 'close' });
          return;
        }
        chunks.push(chunk);
      }
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { return send(res, 400, { error: 'Invalid JSON' }); }
      if (storyAction) {
        try {
          if (storyAction === 'index') {
            const chapter = body?.chapter;
            const chunks = typeof body?.text === 'string' ? storyModule.splitChapter(body.text) : [];
            if (!Number.isInteger(chapter) || chapter < 1 || chapter > 1000 || !chunks.length) {
              return send(res, 400, { error: 'Expected chapter 1–1000 and text up to 12000 characters' });
            }
            await story.index(account.key, book[1], chapter, chunks);
            return send(res, 200, { chunks: chunks.length });
          }
          const query = typeof body?.query === 'string' ? body.query.trim() : '';
          if (!query || query.length > 2000) return send(res, 400, { error: 'Expected query up to 2000 UTF-16 units' });
          return send(res, 200, { passages: await story.search(account.key, book[1], query) });
        } catch { return send(res, 503, { error: 'Story index or embedding provider unavailable' }); }
      }
      const messages = body?.messages;
      if (body?.stream || !Array.isArray(messages) || messages.length < 1 || messages.length > 8 ||
          !messages.every(m => m && ['system', 'user', 'assistant'].includes(m.role) && typeof m.content === 'string')) {
        return send(res, 400, { error: 'Expected non-streaming text messages' });
      }
      if (enqueue) {
        try {
          const id = await jobs.enqueue(account.key, messages.map(({ role, content }) => ({ role, content })));
          return send(res, 202, { id, status: 'queued' }, { Location: `/v1/jobs/${id}` });
        } catch { return send(res, 503, { error: 'Job service unavailable' }); }
      }
      metrics.accepted++;
      const start = Date.now();
      try {
        const response = usage
          ? await meteredGeneration(generate, usage, account.key, openAiModel, messages)
          : await generate(messages);
        metrics.completed++;
        send(res, 200, response);
      } catch {
        metrics.upstreamFailures++;
        send(res, 502, { error: 'Generation provider unavailable' });
      } finally {
        metrics.upstreamLatencyMs += Date.now() - start;
      }
    } catch {
      if (!res.headersSent && !res.destroyed) send(res, 400, { error: "Incomplete request" });
    } finally { active--; }
  }).on('clientError', (_error, socket) => socket.destroy());
}
