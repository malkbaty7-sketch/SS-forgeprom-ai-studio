CREATE TABLE IF NOT EXISTS gateway_invoice_requests (
  reference uuid PRIMARY KEY,
  customer text NOT NULL,
  amount integer NOT NULL,
  currency text NOT NULL,
  description text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  invoice_id text
);
