import { randomUUID } from 'node:crypto';
import { Pool } from 'pg';

export function createJobs(connectionString) {
  const db = new Pool({ connectionString, max: 4, connectionTimeoutMillis: 2000, statement_timeout: 5000 });
  db.on('error', () => console.error('Job database connection failed'));
  return {
    async enqueue(account, messages) {
      const id = randomUUID();
      await db.query('INSERT INTO gateway_jobs(id,account_key,messages) VALUES ($1,$2,$3::jsonb)', [id, account, JSON.stringify(messages)]);
      return id;
    },
    async status(account, id) {
      const { rows } = await db.query('SELECT id,status,result FROM gateway_jobs WHERE id=$1 AND account_key=$2 AND expires_at>now()', [id, account]);
      return rows[0];
    },
    async runOne(generate) {
      await db.query("DELETE FROM gateway_jobs WHERE expires_at <= now()");
      // A lost lease fails rather than automatically rebilling the same generation.
      await db.query("UPDATE gateway_jobs SET status='failed',messages=NULL,lease_until=NULL WHERE status='running' AND lease_until<=now()");
      const { rows: [job] } = await db.query(`UPDATE gateway_jobs SET status='running', lease_until=now()+interval '2 minutes'
        WHERE id=(SELECT id FROM gateway_jobs WHERE status='queued' AND expires_at>now()
                  ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id,messages`);
      if (!job) return false;
      let result = null;
      try { result = await generate(job.messages); } catch { /* report failure without storing provider error */ }
      await db.query("UPDATE gateway_jobs SET status=$2,messages=NULL,result=$3::jsonb,lease_until=NULL WHERE id=$1 AND status='running'",
        [job.id, result === null ? 'failed' : 'done', JSON.stringify(result)]);
      return true;
    }
  };
}
