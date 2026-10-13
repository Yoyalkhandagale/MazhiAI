// Mazhi AI backend starter. Requires Node.js 18+; uses built-in modules only.
// Keep AI_PROVIDER_API_KEY on the server only; never put this key in the Android APK.
import http from 'node:http';

const PORT = Number(process.env.PORT || 8080);
const API_URL = process.env.AI_API_URL || 'https://api.openai.com/v1/chat/completions';
let providerUrl;
try { providerUrl = new URL(API_URL); } catch { throw new Error('AI_API_URL must be a valid HTTPS URL'); }
if (providerUrl.protocol !== 'https:' || !providerUrl.hostname) {
  throw new Error('AI_API_URL must use HTTPS and include a hostname');
}
const API_KEY = process.env.AI_PROVIDER_API_KEY;
const MODEL = process.env.AI_MODEL || 'gpt-4o-mini';
const RATE_LIMIT_WINDOW_MS = Math.max(10_000, Number(process.env.RATE_LIMIT_WINDOW_MS || 60_000));
const RATE_LIMIT_MAX = Math.max(1, Number(process.env.RATE_LIMIT_MAX || 20));
const PROVIDER_TIMEOUT_MS = Math.max(5_000, Number(process.env.PROVIDER_TIMEOUT_MS || 30_000));
const MAX_BODY_BYTES = 16 * 1024;
const buckets = new Map();

function send(res, status, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': 'no-store',
    ...extraHeaders
  });
  res.end(body);
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    let bytes = 0;
    let tooLarge = false;
    req.setEncoding('utf8');
    req.on('data', chunk => {
      bytes += Buffer.byteLength(chunk);
      if (bytes > MAX_BODY_BYTES) {
        tooLarge = true;
        raw = '';
        return; // Keep draining the request so we can return a proper 413 response.
      }
      if (!tooLarge) raw += chunk;
    });
    req.on('end', () => {
      if (tooLarge) {
        const error = new Error('request_too_large');
        error.statusCode = 413;
        reject(error);
        return;
      }
      try { resolve(JSON.parse(raw || '{}')); }
      catch { const error = new Error('invalid_json'); error.statusCode = 400; reject(error); }
    });
    req.on('error', reject);
  });
}

function allowRequest(req, res) {
  const now = Date.now();
  const forwarded = req.headers['x-forwarded-for'];
  // Use the first proxy-provided address only when deployed behind a trusted hosting proxy.
  const key = (typeof forwarded === 'string' ? forwarded.split(',')[0].trim() : '') || req.socket.remoteAddress || 'unknown';
  let bucket = buckets.get(key);
  if (!bucket || now >= bucket.resetAt) bucket = { count: 0, resetAt: now + RATE_LIMIT_WINDOW_MS };
  bucket.count += 1;
  buckets.set(key, bucket);
  if (buckets.size > 5000) {
    for (const [ip, entry] of buckets) if (now >= entry.resetAt) buckets.delete(ip);
  }
  res.setHeader('X-RateLimit-Limit', String(RATE_LIMIT_MAX));
  res.setHeader('X-RateLimit-Remaining', String(Math.max(0, RATE_LIMIT_MAX - bucket.count)));
  if (bucket.count > RATE_LIMIT_MAX) {
    res.setHeader('Retry-After', String(Math.max(1, Math.ceil((bucket.resetAt - now) / 1000))));
    send(res, 429, { error: 'rate_limit_exceeded', reply: 'खूप विनंत्या झाल्या. थोड्या वेळाने पुन्हा प्रयत्न कर.' });
    return false;
  }
  return true;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', 'http://localhost');
  if (req.method === 'GET' && url.pathname === '/health') {
    return send(res, 200, { ok: true, service: 'mazhi-ai-backend' });
  }
  if (req.method !== 'POST' || url.pathname !== '/chat') {
    return send(res, 404, { error: 'not_found' });
  }
  if (!allowRequest(req, res)) return;
  const contentType = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
  if (contentType !== 'application/json') {
    return send(res, 415, { error: 'content_type_must_be_json', reply: 'JSON स्वरूपातील विनंती आवश्यक आहे.' });
  }

  let body;
  try { body = await readJson(req); }
  catch (error) {
    if (res.writableEnded || res.destroyed) return;
    return send(res, error.statusCode || 400, { error: error.message === 'request_too_large' ? 'request_too_large' : 'invalid_json' });
  }

  const message = typeof body?.message === 'string' ? body.message.trim() : '';
  if (!message || message.length > 8000) {
    return send(res, 400, { error: 'message_required_or_too_long', reply: 'संदेश रिकामा आहे किंवा खूप मोठा आहे.' });
  }
  if (!API_KEY) {
    return send(res, 503, { error: 'server_ai_key_not_configured', reply: 'ऑनलाइन AI अजून कॉन्फिगर केलेला नाही.' });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), PROVIDER_TIMEOUT_MS);
  try {
    const upstream = await fetch(providerUrl, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${API_KEY}`, 'Content-Type': 'application/json' },
      signal: controller.signal,
      body: JSON.stringify({
        model: MODEL,
        messages: [
          { role: 'system', content: 'You are Mazhi AI, a helpful, honest and privacy-conscious assistant. Reply in Marathi when the user writes Marathi. Do not claim to have feelings or abilities you do not have. Keep replies clear and practical.' },
          { role: 'user', content: message }
        ],
        temperature: 0.7,
        max_tokens: 700
      })
    });
    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      console.error('AI provider request failed with status', upstream.status);
      return send(res, 502, { error: 'ai_provider_request_failed', reply: 'AI सेवा सध्या उत्तर देऊ शकत नाही. थोड्या वेळाने पुन्हा प्रयत्न कर.' });
    }
    const reply = data?.choices?.[0]?.message?.content;
    if (typeof reply !== 'string' || !reply.trim()) {
      return send(res, 502, { error: 'ai_provider_empty_reply', reply: 'AI कडून रिकामं उत्तर आलं. पुन्हा प्रयत्न कर.' });
    }
    return send(res, 200, { reply: reply.trim() });
  } catch (error) {
    const timedOut = error?.name === 'AbortError';
    return send(res, 502, {
      error: timedOut ? 'ai_provider_timeout' : 'ai_provider_unreachable',
      reply: timedOut ? 'AI उत्तर देण्यासाठी जास्त वेळ घेत आहे. पुन्हा प्रयत्न कर.' : 'AI सेवा गाठता आली नाही. सर्व्हरची जोडणी तपासा.'
    });
  } finally {
    clearTimeout(timeout);
  }
});

server.listen(PORT, '0.0.0.0', () => console.log(`Mazhi AI backend listening on ${PORT}`));
