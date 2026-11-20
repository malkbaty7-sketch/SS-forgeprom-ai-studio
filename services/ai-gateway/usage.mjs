import { Pool } from 'pg';

export function tokenCounts(response) {
  const usage = response?.usage;
  const values = [usage?.prompt_tokens, usage?.completion_tokens];
  return values.every(value => Number.isSafeInteger(value) && value >= 0) ? values : null;
}

export function createUsage(connectionString) {
  const db = new Pool({ connectionString, max: 3, connectionTimeoutMillis: 2000, statement_timeout: 3000 });
  db.on('error', () => console.error('Usage database connection failed'));
  return {
    async record(account, model, response) {
      const counts = tokenCounts(response);
      if (!counts) return;
      await db.query(`INSERT INTO gateway_usage VALUES ($1, (now() AT TIME ZONE 'UTC')::date, $2, 1, $3, $4)
        ON CONFLICT (account_key, usage_day, model) DO UPDATE SET
          requests = gateway_usage.requests + 1,
          prompt_tokens = gateway_usage.prompt_tokens + excluded.prompt_tokens,
          completion_tokens = gateway_usage.completion_tokens + excluded.completion_tokens`, [account, model, ...counts]);
    },
    async recent(account) {
      const { rows } = await db.query(`SELECT usage_day::text AS day, model, requests, prompt_tokens, completion_tokens
        FROM gateway_usage WHERE account_key = $1 AND usage_day >= (now() AT TIME ZONE 'UTC')::date - 29
        ORDER BY usage_day DESC, model`, [account]);
      return rows;
    }
  };
}

export async function meteredGeneration(generate, usage, account, model, messages) {
  const response = await generate(messages);
  try { await usage?.record(account, model, response); }
  catch { console.error('Could not record generation usage'); }
  return response;
}
