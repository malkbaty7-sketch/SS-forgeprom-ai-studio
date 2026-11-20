import { generate } from './provider.mjs';
import { createServer } from 'node:http';
import { createAccess, consume } from './access.mjs';

const { GATEWAY_TOKEN, GATEWAY_ACCOUNTS, DATABASE_URL, OPENAI_API_KEY, OPENAI_MODEL, PORT = '8080' } = process.env;
if (!OPENAI_API_KEY || !OPENAI_MODEL) throw new Error('Configure OPENAI_API_KEY and OPENAI_MODEL');
const authenticate = createAccess(GATEWAY_ACCOUNTS, GATEWAY_TOKEN);
const takeQuota = DATABASE_URL
  ? (await import('./shared-quota.mjs')).createSharedQuota(DATABASE_URL)
  : consume;
const jobs = DATABASE_URL && process.env.ENABLE_JOBS === '1'
  ? (await import('./jobs.mjs')).createJobs(DATABASE_URL) : null;
let active = 0;
const metrics = { accepted: 0, upstreamFailures: 0, completed: 0, upstreamLatencyMs: 0 };

function send(res, status, payload, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers });
  res.end(JSON.stringify(payload));
}

createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') return send(res, 200, { ok: true });
  const account = authenticate(req.headers.authorization);
  if (!account) return send(res, 401, { error: 'Unauthorized' });
  if (req.method === 'GET' && req.url === '/metrics') {
    return account.admin ? send(res, 200, { ...metrics, active }) : send(res, 403, { error: 'Forbidden' });
  }
  const jobId = /^\/v1\/jobs\/([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})$/.exec(req.url)?.[1];
  if (jobs && req.method === 'GET' && jobId) {
    try {
      const job = await jobs.status(account.key, jobId);
      return job ? send(res, 200, job) : send(res, 404, { error: 'Not found' });
    } catch { return send(res, 503, { error: 'Job service unavailable' }); }
  }
  const enqueue = jobs && req.url === '/v1/jobs';
  if (req.method !== 'POST' || (!enqueue && req.url !== '/v1/chat/completions')) return send(res, 404, { error: 'Not found' });
  if (active >= 4) return send(res, 429, { error: 'Gateway busy' }, { 'Retry-After': '2' });
  active++;
  try {
    let retryAfter;
    try { retryAfter = await takeQuota(account); }
    catch { return send(res, 503, { error: 'Quota service unavailable' }); }
    if (retryAfter) return send(res, 429, { error: 'Account limit reached' }, { 'Retry-After': String(retryAfter) });
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
      const response = await generate(messages);
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
}).on('clientError', (_error, socket) => socket.destroy()).listen(Number(PORT), '0.0.0.0');
