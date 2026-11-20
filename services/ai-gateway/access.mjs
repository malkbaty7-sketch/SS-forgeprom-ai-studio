import { createHash } from 'node:crypto';

const limits = Object.freeze({ free: 10, premium: 60 });
export const digest = token => createHash('sha256').update(token).digest('hex');

export function createAccess(raw, legacyToken) {
  const entries = raw ? JSON.parse(raw) : [];
  if (!Array.isArray(entries)) throw new Error('Invalid gateway accounts');
  const accounts = new Map(), ids = new Set();
  for (const { id, hash, plan } of entries) {
    if (typeof id !== 'string' || !id || ids.has(id) || typeof hash !== 'string' ||
        !/^[a-f0-9]{64}$/.test(hash) || accounts.has(hash) || !Object.hasOwn(limits, plan)) {
      throw new Error('Invalid or duplicate gateway account');
    }
    ids.add(id);
    accounts.set(hash, { key: `user:${id}`, limit: limits[plan], admin: false, window: -1, used: 0 });
  }
  if (legacyToken && Buffer.byteLength(legacyToken) >= 32) {
    const hash = digest(legacyToken);
    if (accounts.has(hash)) throw new Error('Admin token must differ from account tokens');
    accounts.set(hash, { key: "admin", limit: 30, admin: true, window: -1, used: 0 });
  }
  if (!accounts.size) throw new Error('Configure gateway credentials');
  return authorization => {
    if (typeof authorization !== 'string' || !authorization.startsWith('Bearer ')) return null;
    return accounts.get(digest(authorization.slice(7))) ?? null;
  };
}

export function consume(account, now = Date.now()) {
  const window = Math.floor(now / 60_000);
  if (account.window !== window) { account.window = window; account.used = 0; }
  if (account.used >= account.limit) return Math.ceil(((window + 1) * 60_000 - now) / 1000);
  account.used++;
  return 0;
}
