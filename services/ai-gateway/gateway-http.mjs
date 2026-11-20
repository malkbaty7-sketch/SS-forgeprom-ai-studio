import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { generate } from './provider.mjs';
import { BillingError } from './billing.mjs';
import { webQuery } from './web-search.mjs';
import { meteredGeneration } from './usage.mjs';

const REQUEST_ID = /^[A-Za-z0-9._-]{1,128}$/;

export function createGateway({
  authenticate, takeQuota, jobs, usage, story, storyModule, billing, webSearch, openAiModel,
  readiness = [], logger = console, clock = Date.now
}) {
  let active = 0;
  const metrics = { accepted: 0, upstreamFailures: 0, completed: 0, upstreamLatencyMs: 0 };

  function log(event) {
    try { logger?.info?.(JSON.stringify(event)); } catch { /* logging must never fail a request */ }
  }

  function send(res, status, payload, headers = {}) {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers });
    res.end(JSON.stringify(payload));
  }

  const server = createServer(async (req, res) => {
    const incoming = req.headers['x-request-id'];
    const requestId = typeof incoming === 'string' && REQUEST_ID.test(incoming) ? incoming : randomUUID();
    res.setHeader('X-Request-Id', requestId);
    const started = clock();
    let accountKey = null;
    res.on('finish', () => log({
      level: 'info', event: 'request', requestId, method: req.method,
      path: String(req.url ?? '').split('?')[0], status: res.statusCode,
      durationMs: clock() - started, account: accountKey
    }));

    if (req.method === 'GET' && req.url === '/health') return send(res, 200, { ok: true });
    if (req.method === 'GET' && req.url === '/readyz') {
      const checks = {};
      let ready = true;
      await Promise.all((Array.isArray(readiness) ? readiness : []).map(async probe => {
        let ok = false;
        try { ok = (await probe.check()) === true; } catch { ok = false; }
        checks[probe.name] = ok ? 'ok' : 'failed';
        if (!ok) ready = false;
      }));
      return send(res, ready ? 200 : 503, { ready, checks });
    }
    const account = authenticate(req.headers.authorization);
    if (!account) return send(res, 401, { error: 'Unauthorized' });
    accountKey = account.key;
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
        log({ level: 'warn', event: 'upstream_failure', requestId });
        send(res, 502, { error: 'Generation provider unavailable' });
      } finally {
        metrics.upstreamLatencyMs += Date.now() - start;
      }
    } catch {
      if (!res.headersSent && !res.destroyed) send(res, 400, { error: "Incomplete request" });
    } finally { active--; }
  }).on('clientError', (_error, socket) => socket.destroy());

  // حدود زمنية صريحة: لا نترك اتصالاً بطيئاً يحتجز الخادم إلى ما لا نهاية.
  server.requestTimeout = 60_000;
  server.headersTimeout = 10_000;
  return server;
}
