# Deploy Mazhi AI backend (Render example)

This is a deployment recipe, not a deployment already performed by the project author.

1. Push the `backend-template` directory to a private or public GitHub repository. Never commit `.env`, provider API keys, or tokens.
2. In Render, create a Web Service from that repository and set the root directory to `backend-template` (or create the service using `render.yaml` if your repository layout supports it).
3. Set `AI_PROVIDER_API_KEY` in the hosting dashboard's secret/environment settings. Do not paste it into Android code, app settings, screenshots, or chat.
4. Check `AI_API_URL` and `AI_MODEL` against the provider account and model you actually have access to. A model name in this sample is only a default, not a promise of account access.
5. Deploy, then open `https://YOUR-SERVICE.onrender.com/health`. It should return JSON with `"ok": true`.
6. In Mazhi AI settings, set the endpoint to `https://YOUR-SERVICE.onrender.com/chat`.
7. Test one non-sensitive message. Do not send passwords, payment data, or private information unless you have reviewed the AI provider's data handling terms.

## Protections and limits

- HTTPS is expected by the Android app.
- `/chat` has a basic per-IP in-memory rate limit (default 20 requests per 60 seconds) and a provider request timeout.
- The limiter is per server process and resets on restart. For a multi-instance production deployment, use a shared rate-limit store and add stronger app/user authentication.
- `/health` is public and does not test whether the provider key works.
- This starter does not intentionally persist or log message content, but the AI provider receives each message to generate a reply. Hosting/provider infrastructure may retain operational data under their own policies.
- Review provider pricing and set spending limits/alerts before sharing the app widely.
