# Implementation status — 2026-10-19

## User-supplied API keys (2026-10-19)
- **No API keys are built into the app any more.** The `BuildConfig` key fields and the `local.properties` reading were removed from `app/build.gradle.kts`; `SettingsRepository.apiKey()` returns only keys the user saved.
- Settings → **API kalitlari** has an editor per provider (VirusTotal, Safe Browsing, Gemini): masked input with show/hide, save, "test connection", replace, remove, and a "how to get a key" note with a link to the provider's key page.
- Verified on the emulator: a fresh install shows every service as "Sozlanmagan"; keys entered through the UI tested OK (HTTP 200) and the phishing URL, APK and AI-explanation flows worked. A string search of the built APK found none of the old keys.
- `./gradlew :app:testDebugUnitTest`: ✅ 91 tests, 0 failures.

## Verified results (actually executed)

| Check | Result |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL (`app-debug.apk`, about 24 MB) |
| `./gradlew :app:testDebugUnitTest` | ✅ 88 tests, 0 failures, 0 errors |
| Installed on emulator `Medium_Phone` (API 37) | ✅ Launches with no crash; onboarding → home (Uzbek) → URL checker exercised |
| Backend `pytest` | ✅ 104 passed |
| Alembic `upgrade head` / `downgrade base` (SQLite) | ✅ |
| Backend live run (uvicorn, fixture mode): `/ready`, register, URL scan, `/openapi.json` | ✅ |
| `docker compose up` (PostgreSQL) | ⚠️ **Not executed**: no Docker runtime on this machine |
| Real provider calls with real keys | ⚠️ **Not executed**: no API keys were available; the integrations follow the official docs and are tested against mocked responses |
| Android instrumented/UI tests | ❌ Not written |

A manual emulator test found a bug: `paypa1-login.xyz` was rated LOW because a brand disguised inside a longer host was missed. It was fixed in both Android and the backend, with regression tests added.

## Built-in API keys (2026-10-18) — superseded on 2026-10-19, see above
- The VirusTotal, Safe Browsing and Claude keys are read from git-ignored `local.properties` at build time and compiled into `BuildConfig`. A key the user enters in the app takes precedence.
- Settings now shows only the Claude AI key status.
- Tested live on the emulator with the real keys:
  - VirusTotal URL report: ✅ 10/93 engines for Google's test phishing page.
  - Safe Browsing v5: ✅ `SOCIAL_ENGINEERING`.
  - Claude: ❌ key accepted, but the account has no credits ("credit balance is too low").
- Fixed: Safe Browsing v5 returns **protobuf only** (JSON is rejected with "Unsupported Output Format"). The app now decodes the protobuf, and a test uses the real response bytes.
- ⚠️ Keys compiled into an APK can be extracted by anyone who has the APK. Do not distribute this build publicly. For distribution, use the backend so the keys stay on the server.

## AI provider switched to Google Gemini (2026-10-18)
- Android AI explanations now use the Gemini API (`gemini-2.5-flash`, structured JSON output via `responseSchema`). `GeminiExplainer` and `GeminiProtocol` replace the Claude client, with the same schema, validation and untrusted-data rules.
- Settings shows the Gemini key status plus a live "key working" check (a model-metadata request that uses no generation quota).
- Verified on the emulator: the Settings key check returned HTTP 200, and the explanation of the phishing test URL came back in Uzbek in about 11 s.
- `gemini-flash-latest` took about 100 s per answer and overran the HTTP timeout, so the app pins `gemini-2.5-flash` and gives AI calls a 90 s read timeout.
- Free tier: Google may use submitted data to improve its services. This is stated in the app's privacy text.
- The backend still uses Claude (`app/ai/analyst.py`); it is not switched.

## Branding, theme, animations, risk fix (2026-10-18)
- **App icon:** an adaptive icon generated from `dizayn/logo.png` (shield cropped into the 72dp visible area, navy `#000B24` background), a monochrome layer for Android 13 themed icons, and every density from mdpi to xxxhdpi. The full logo is shown on the Settings header.
- **Startup:** the system splash (core-splashscreen) hands off to a Compose intro (the shield springs in, a glow ring pulses, a scan arc sweeps, then the name and tagline rise in) in about 1.6 s, once per cold start.
- **Motion:** slide and fade transitions between screens, a staggered entrance on Home and Settings, and animated Settings sub-pages.
- **Theme:** System / Light / Dark, applied in `Application.onCreate` with no flash.
- **Settings regrouped:** brand header → Appearance (theme and language) → AI (Gemini status, live check, toggle) → Scanning → Privacy → About.
- **Risk-policy bug fixed:** official Telegram 13.0.3 (VirusTotal 0/67) was rated **CRITICAL** because permission weights were summed. Now:
  - Permissions alone cap at MEDIUM.
  - Integrity findings (signature, certificate, downgrade) cap at HIGH.
  - When at least 20 VirusTotal engines are clean, permission-only findings drop to LOW, unless an overlay + accessibility abuse pattern is present.
  - Regression tests were added. Records saved before the fix keep their old verdict until rescanned.

## Implemented

**Android**
- Onboarding and privacy explanation.
- Home dashboard: honest overview, entry points, recent scans, last successful scan, service status, limitations.
- APK scanner:
  - SAF and share-intent input; name, MIME, magic-byte and size validation; streaming SHA-256, SHA-1 and MD5.
  - PackageManager metadata, `apksig` v1–v4 verification, signer-change and version-downgrade detection against history.
  - 12 sensitive-capability categories with context explanations, plus permission-combo heuristics.
  - VirusTotal hash lookup → upload only with consent → polling with timeout, cancel and retry limit.
  - "No existing report found." is never shown as clean.
- URL checker:
  - Strict normalization and scheme validation; punycode, mixed-script, lookalike, IP, shortener, TLD and port checks.
  - Safe Browsing v5 or Web Risk, plus VirusTotal URL reports; never fetches the URL.
  - "Open anyway" goes through a warning dialog and then an external browser.
- Installed-app audit: install source, system vs user app, signer, granted dangerous permissions, accessibility, device-admin, notification-listener and overlay access, optional VirusTotal hash lookup, and an honest scope banner.
- AI analyst: Claude structured output (the schema fields from the spec), validation, untrusted-data prompt, and the deterministic verdict always wins.
- History (Room): delete one or clear all; minimized URLs; provider status and incomplete flags.
- Settings: encrypted keys, language (uz/ru/en/system), upload consent toggle, AI toggle, notifications (opt-in, runtime permission), service status with live "Test connection", privacy controls.
- Light and dark theme.

**Backend**: all endpoints from spec §9, plus the following:
- Bearer auth with hashed tokens and ownership checks (404 on other clients' scans).
- Rate limiting, body-size limits, correlation IDs, redacted JSON logs.
- Retries with backoff, circuit breaker, quota handling.
- Deterministic policy engine and AI analyst with grounding.
- Alembic migration, Dockerfile and compose, fixture mode that is refused in production.

## Partially implemented / limitations
- **The Android app does not use the backend yet.** It calls the providers directly using the user's own keys, as the user asked for an Android-only build first. The backend is ready for a "server mode" in which the app sends hash and metadata to `/api/v1/scans/apk`; that switch is not built. A shared or commercial distribution must use the backend so that provider secrets never ship in the client.
- **Two risk policies.** The Android and backend policies are separate implementations of the same rules (both unit-tested). Android uses `NO_DETECTIONS` where the backend says `LOW`. Exact thresholds differ slightly; for example, URL VirusTotal ≥2 is HIGH on Android and ≥5 on the backend.
- **Backend APK upload.** It does structural ZIP checks only. Manifest and permission parsing happens on the device and is sent as untrusted metadata.
- **Concurrency.** In-memory job runner and rate limiter, so a single backend instance only (see the backend README).
- **AI cost.** AI explanations run only on demand (Android) or per scan when enabled (backend).
- **Scan-completion notifications.** Wired for APK and URL scans. The installed-app audit does not notify, because it is interactive.

## Not implemented
- Android UI/instrumented tests (Espresso or Compose UI tests).
- WorkManager background jobs. These were not needed: VirusTotal polling runs in an app-scoped coroutine while the process is alive.
- Redirect-inspection service. Deliberately omitted: no URL is ever fetched, so there is no SSRF surface.
- Release signing config. Instructions are in the README, and no keystore is committed.

## Open security items
- API keys live on the client in direct mode. This is acceptable only when users bring their own keys.
- The privacy policy is a template and needs legal review.
- The Play Console declarations for `QUERY_ALL_PACKAGES` and `REQUEST_DELETE_PACKAGES` are required before publishing.
