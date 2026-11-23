import test from 'node:test';
import assert from 'node:assert/strict';
import { generate } from './provider.mjs';

test('generate posts sanitized messages to OpenAI-compatible endpoint', async () => {
  const originalFetch = global.fetch;
  try {
    const calls = [];
    global.fetch = async (url, options) => {
      calls.push({ url, options });
      return {
        ok: true,
        async json() { return { choices: [{ message: { content: 'hello' } }] }; }
      };
    };
    const response = await generate(
      [{ role: 'user', content: 'hi', extra: 'ignored' }],
      { OPENAI_API_KEY: 'sk-test', OPENAI_MODEL: 'gpt-test' }
    );
    assert.deepEqual(response, { choices: [{ message: { content: 'hello' } }] });
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, 'https://api.openai.com/v1/chat/completions');
    assert.equal(calls[0].options.method, 'POST');
    assert.equal(calls[0].options.redirect, 'error');
    assert.equal(calls[0].options.headers.Authorization, 'Bearer sk-test');
    const body = JSON.parse(calls[0].options.body);
    assert.equal(body.model, 'gpt-test');
    assert.deepEqual(body.messages, [{ role: 'user', content: 'hi' }]);
    assert.equal(body.max_completion_tokens, 4096);
  } finally {
    global.fetch = originalFetch;
  }
});

test('generate throws generic error on upstream failure', async () => {
  const originalFetch = global.fetch;
  try {
    global.fetch = async () => ({
      ok: false,
      status: 500,
      body: { async cancel() {} }
    });
    await assert.rejects(
      () => generate([{ role: 'user', content: 'hi' }], { OPENAI_API_KEY: 'k', OPENAI_MODEL: 'm' }),
      /Upstream error/
    );
  } finally {
    global.fetch = originalFetch;
  }
});
