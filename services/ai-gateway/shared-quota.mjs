import { Pool } from 'pg';

// PostgreSQL time is shared across gateway processes. The row lock makes
// checking and consuming a slot a single atomic operation.
export const TAKE_SLOT = `
INSERT INTO gateway_quota (account_key, window_id, used)
VALUES ($1, floor(extract(epoch FROM statement_timestamp()) / 60)::bigint, 1)
ON CONFLICT (account_key) DO UPDATE SET
  window_id = excluded.window_id,
  used = CASE WHEN gateway_quota.window_id = excluded.window_id THEN gateway_quota.used + 1 ELSE 1 END
WHERE gateway_quota.window_id < excluded.window_id
   OR (gateway_quota.window_id = excluded.window_id AND gateway_quota.used < $2)
RETURNING used`;

export function createSharedQuota(connectionString) {
  const pool = new Pool({ connectionString, max: 5, connectionTimeoutMillis: 2000, query_timeout: 3000, statement_timeout: 3000 });
  pool.on('error', () => console.error('Shared quota database connection failed'));
  const take = account => takeSlot(pool, account);
  // فحص جاهزية خفيف: لا يكشف بيانات، ويتحقق فقط من إمكانية الوصول للقاعدة.
  take.ready = async () => {
    await pool.query('SELECT 1');
    return true;
  };
  return take;
}

export async function takeSlot(db, account) {
  const result = await db.query(TAKE_SLOT, [account.key, account.limit]);
  return result.rowCount === 1 ? 0 : 60;
}
