import { Pool } from 'pg';

export function splitChapter(text) {
  const chars = [...text.trim()];
  if (!chars.length || chars.length > 12_000) return [];
  const chunks = [];
  for (let start = 0; start < chars.length; start += 850) {
    chunks.push(chars.slice(start, start + 1000).join(''));
    if (start + 1000 >= chars.length) break;
  }
  return chunks;
}

export function embeddingVectors(data, count) {
  if (!Array.isArray(data) || data.length !== count) throw new Error('Invalid embeddings');
  const vectors = Array(count).fill(null);
  for (const { index, embedding } of data) {
    if (!Number.isInteger(index) || index < 0 || index >= count || vectors[index] ||
        !Array.isArray(embedding) || embedding.length !== 1536 || !embedding.every(Number.isFinite) ||
        embedding.every(value => value === 0)) throw new Error('Invalid embedding');
    vectors[index] = JSON.stringify(embedding);
  }
  return vectors;
}

export function createStory(connectionString, apiKey) {
  const db = new Pool({ connectionString, max: 4, connectionTimeoutMillis: 2000, statement_timeout: 5000 });
  db.on('error', () => console.error('Story database connection failed'));
  async function embed(input) {
    const response = await fetch('https://api.openai.com/v1/embeddings', {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30_000),
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: 'text-embedding-3-small', dimensions: 1536, input })
    });
    if (!response.ok) { await response.body?.cancel(); throw new Error('Embedding provider unavailable'); }
    return embeddingVectors((await response.json()).data, input.length);
  }
  return {
    async index(account, book, chapter, chunks) {
      const vectors = await embed(chunks);
      const client = await db.connect();
      try {
        await client.query('BEGIN');
        await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1, 0))', [`${account}/${book}`]);
        await client.query('DELETE FROM gateway_story_chunks WHERE account_key=$1 AND book_id=$2 AND chapter=$3', [account, book, chapter]);
        await client.query(`INSERT INTO gateway_story_chunks
          SELECT $1, $2, $3, ordinality::integer, content, embedding::vector
          FROM unnest($4::text[], $5::text[]) WITH ORDINALITY AS items(content, embedding, ordinality)`, [account, book, chapter, chunks, vectors]);
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK'); throw error; }
      finally { client.release(); }
    },
    async search(account, book, query) {
      const [vector] = await embed([query]);
      const { rows } = await db.query(`SELECT chapter, chunk, content FROM gateway_story_chunks
        WHERE account_key=$1 AND book_id=$2 ORDER BY embedding <=> $3::vector, chapter, chunk LIMIT 5`, [account, book, vector]);
      return rows;
    },
    async remove(account, book) {
      await db.query('DELETE FROM gateway_story_chunks WHERE account_key=$1 AND book_id=$2', [account, book]);
    }
  };
}
