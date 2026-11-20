CREATE TABLE IF NOT EXISTS gateway_quota (
  account_key text PRIMARY KEY,
  window_id bigint NOT NULL,
  used integer NOT NULL CHECK (used > 0)
);
