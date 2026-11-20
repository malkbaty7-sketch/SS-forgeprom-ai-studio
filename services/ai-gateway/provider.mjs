export async function generate(messages, { OPENAI_API_KEY, OPENAI_MODEL } = process.env) {
      const upstream = await fetch('https://api.openai.com/v1/chat/completions', {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(45_000),
        headers: { Authorization: `Bearer ${OPENAI_API_KEY}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: OPENAI_MODEL, messages: messages.map(({ role, content }) => ({ role, content })), max_completion_tokens: 4096 })
      });
      if (!upstream.ok) { await upstream.body?.cancel(); throw new Error('Upstream error'); }
      return upstream.json();
}
