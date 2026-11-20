CREATE TABLE IF NOT EXISTS gateway_jobs (
  id uuid PRIMARY KEY,
  account_key text NOT NULL,
  status text NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','running','done','failed')),
  messages jsonb,
  result jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  lease_until timestamptz,
  expires_at timestamptz NOT NULL DEFAULT now() + interval '1 day'
);
CREATE INDEX IF NOT EXISTS gateway_jobs_pending ON gateway_jobs (created_at) WHERE status = 'queued';
