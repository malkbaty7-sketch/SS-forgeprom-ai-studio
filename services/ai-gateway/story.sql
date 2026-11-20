CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS gateway_story_chunks (
  account_key text NOT NULL,
  book_id uuid NOT NULL,
  chapter integer NOT NULL CHECK (chapter BETWEEN 1 AND 1000),
  chunk integer NOT NULL,
  content text NOT NULL,
  embedding vector(1536) NOT NULL,
  PRIMARY KEY (account_key, book_id, chapter, chunk)
);
