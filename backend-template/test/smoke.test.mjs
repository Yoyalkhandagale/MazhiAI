import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';

const port = 18080 + Math.floor(Math.random() * 1000);
const base = `http://127.0.0.1:${port}`;
let server;

before(async () => {
  server = spawn(process.execPath, ['server.mjs'], {
    cwd: new URL('..', import.meta.url),
    env: { ...process.env, PORT: String(port), AI_PROVIDER_API_KEY: '', RATE_LIMIT_MAX: '30' },
    stdio: 'ignore'
  });
  const deadline = Date.now() + 8000;
  while (Date.now() < deadline) {
    if (server.exitCode !== null) throw new Error(`Backend exited early (${server.exitCode})`);
    try {
      const response = await fetch(`${base}/health`);
      if (response.ok) return;
    } catch { /* server is still starting */ }
    await new Promise(resolve => setTimeout(resolve, 150));
  }
  throw new Error('Backend did not become healthy within 8 seconds');
});

after(() => {
  if (server && server.exitCode === null) server.kill('SIGTERM');
});

test('health endpoint returns service status and safe headers', async () => {
  const response = await fetch(`${base}/health`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: 'mazhi-ai-backend' });
  assert.equal(response.headers.get('x-powered-by'), null);
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('empty message is rejected', async () => {
  const response = await fetch(`${base}/chat`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: '   ' })
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, 'message_required_or_too_long');
});

test('oversized message is rejected', async () => {
  const response = await fetch(`${base}/chat`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'x'.repeat(8001) })
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, 'message_required_or_too_long');
});

test('non-JSON content type is rejected', async () => {
  const response = await fetch(`${base}/chat`, {
    method: 'POST', headers: { 'content-type': 'text/plain' }, body: 'hello'
  });
  assert.equal(response.status, 415);
  assert.equal((await response.json()).error, 'content_type_must_be_json');
});

test('request body above the byte limit gets a proper 413 response', async () => {
  const response = await fetch(`${base}/chat`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ message: 'x'.repeat(17000) })
  });
  assert.equal(response.status, 413);
  assert.equal((await response.json()).error, 'request_too_large');
});

test('online chat fails safely when server key is not configured', async () => {
  const response = await fetch(`${base}/chat`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'नमस्कार' })
  });
  assert.equal(response.status, 503);
  assert.equal((await response.json()).error, 'server_ai_key_not_configured');
});

test('malformed JSON is rejected with a safe error', async () => {
  const response = await fetch(`${base}/chat`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: '{broken'
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, 'invalid_json');
});

test('unknown route returns 404 without server details', async () => {
  const response = await fetch(`${base}/not-a-route`);
  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { error: 'not_found' });
});


test('server-side provider URL must use HTTPS', async () => {
  const invalid = spawn(process.execPath, ['server.mjs'], {
    cwd: new URL('..', import.meta.url),
    env: { ...process.env, PORT: String(port + 2000), AI_API_URL: 'http://example.com/chat', AI_PROVIDER_API_KEY: 'not-a-real-key' },
    stdio: 'ignore'
  });
  const exitCode = await new Promise(resolve => {
    const timer = setTimeout(() => { invalid.kill('SIGTERM'); resolve(null); }, 3000);
    invalid.once('exit', code => { clearTimeout(timer); resolve(code); });
  });
  if (invalid.exitCode === null) invalid.kill('SIGTERM');
  assert.equal(exitCode, 1);
});


test('rate limit rejects requests after the configured maximum', async () => {
  let last;
  for (let i = 0; i < 31; i++) {
    last = await fetch(`${base}/chat`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' })
    });
  }
  assert.equal(last.status, 429);
  assert.equal((await last.json()).error, 'rate_limit_exceeded');
  assert.ok(Number(last.headers.get('retry-after')) >= 1);
});
