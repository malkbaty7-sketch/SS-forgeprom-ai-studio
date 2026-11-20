CREATE TABLE IF NOT EXISTS gateway_usage (
  account_key text NOT NULL,
  usage_day date NOT NULL,
  model text NOT NULL,
  requests bigint NOT NULL CHECK (requests >= 0),
  prompt_tokens bigint NOT NULL CHECK (prompt_tokens >= 0),
  completion_tokens bigint NOT NULL CHECK (completion_tokens >= 0),
  PRIMARY KEY (account_key, usage_day, model)
);
