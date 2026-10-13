# Mazhi AI — Latest hardening update

- Backend now rejects non-JSON chat requests with HTTP 415.
- Oversized request bodies are drained safely and receive HTTP 413 instead of an abrupt socket reset.
- Local backend suite: **10 tests passed** in this workspace. This does not mean a cloud deployment or Android APK build has occurred.

# Mazhi AI — Android source + backend starter

## Android app features in this starter
- Marathi-first chat interface
- Speech input via Android speech recognizer (device/language availability varies)
- Text-to-speech reply playback
- Local chat history stored in app-private SharedPreferences, capped at the latest 100 displayed messages
- Online response size is capped in the Android client to reduce accidental memory use
- Marathi / Hindi / English voice input and text-to-speech language selection (system language support varies)
- Local-only personal notes/memories saved in app-private storage; they are not included in online requests
- Settings screen to set an HTTPS backend URL and clear local chat history
- Offline fallback replies when no endpoint is configured or the request fails
- Online request contract: `POST {"message":"..."}` and response `{"reply":"..."}`
- While an online request is pending, message controls are disabled to avoid removing or mismatching the wrong chat bubble
- Backend starter includes request-size validation, basic per-IP rate limiting, security response headers, an upstream timeout, a 700-token response cap, and rejects non-HTTPS AI provider URLs

## Online AI backend starter
The `backend-template` folder contains a Node.js 18+ backend using built-in HTTP and fetch APIs (no runtime npm dependencies) for an OpenAI-compatible Chat Completions endpoint. It also includes Node built-in smoke tests for health, validation, and missing-key behavior. See `backend-template/DEPLOYMENT.md` for a guided Render deployment example and `backend-template/render.yaml` for a starter service definition.

1. Locally, run `cd backend-template`, then `npm test` to run the backend smoke tests. These tests do not call a paid AI provider.
2. Deploy it to a server you control that supports HTTPS.
3. Set `AI_PROVIDER_API_KEY` as a server-side secret/environment variable. Never place this key in the APK or GitHub source.
4. Set `AI_API_URL` and `AI_MODEL` for the provider/model you actually use.
5. The Android app Settings URL should be your deployed endpoint, for example `https://your-domain.example/chat`.
6. Test `GET /health` before using the app.

This starter backend does not intentionally save or log chat content, but the AI provider receives the message for generating a reply. Review the provider's privacy and retention terms before using it with sensitive information. The local Android history and personal notes remain on the device unless the user manually shares data. Personal notes are not sent to the online backend by this starter.

## Build notes
Open this folder as a Gradle project in Android Studio or a compatible Android IDE. The project uses Android Gradle Plugin 8.6.1, compile SDK 35, Java source, and min SDK 23. A compatible JDK/SDK and Gradle sync are required. This environment did not run a full Android build, so this is source code—not a verified APK.

## Current limitations
- Offline answers are intentionally simple; a capable local language model is not bundled.
- Speech recognition can require a system speech service or internet depending on the device.
- The online backend is a starter template and must be deployed/configured before online AI works.
- Before production use, add stronger request authentication, shared rate limiting for multi-instance deployments, abuse protection, and provider-specific error handling to the backend. The included rate limiter is a basic starter safeguard, not a complete production security system.

## Automatic backend checks

The repository includes a GitHub Actions workflow at `.github/workflows/backend-check.yml`. After the project is pushed to GitHub, it checks JavaScript syntax and runs the backend smoke tests on pushes/pull requests that change `backend-template/`. The backend smoke tests were run locally in this workspace and passed (10/10 at the latest update). GitHub Actions will run the same checks after push.

## Security hardening

The Android settings screen validates the configured backend URL as HTTPS with a hostname and rejects URLs containing embedded credentials. The backend also refuses to start if `AI_API_URL` is malformed or is not HTTPS, preventing the provider key from being sent to an insecure HTTP endpoint.

## Current release status

This repository is source code, not a built APK. The online AI backend is a deployment template and is not live until you deploy it and set `AI_PROVIDER_API_KEY` in the hosting provider's secret settings. The Android app has not yet been built or tested on a physical phone from this environment. Do not put an AI provider key in Android source code or the APK.

## Automatic Android APK build on GitHub

The `.github/workflows/android-build.yml` workflow builds a **debug APK** in GitHub Actions whenever Android source/build files change, and also supports manual runs from the Actions tab (`workflow_dispatch`). After a successful run, open the workflow run and download the `MazhiAI-debug-apk` artifact. This requires pushing the project to GitHub first. A debug APK is for testing, not a signed store release. This workflow has been added but has not run from this environment.

For Render, `backend-template/render.yaml` now sets `rootDir: backend-template` so Render runs the Node commands from the backend folder when the blueprint is created from the repository root.

## Owner setup guide

For the exact owner-operated GitHub Actions APK build and Render deployment steps in Marathi, see [`FINAL_SETUP_MR.md`](FINAL_SETUP_MR.md). Cloud deployment and APK artifact creation are not claimed until those external workflows actually complete.
