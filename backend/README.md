# APK Qo‘riqchi AI — Backend

This is a FastAPI service. It runs APK and URL reputation scans against VirusTotal v3, Google Safe Browsing v5 or Web Risk, applies a deterministic risk policy, and gets schema-validated explanations from Claude. Provider API keys stay on the server.

## Quick start (local, no Docker)

```bash
cd backend
python3.10 -m venv .venv && .venv/bin/pip install -e ".[dev]"
cp .env.example .env                      # fill in keys; DATABASE_URL may stay sqlite for dev
export DATABASE_URL=sqlite:///./dev.db
.venv/bin/alembic upgrade head
.venv/bin/uvicorn app.main:app --reload --port 8000
# OpenAPI docs: http://localhost:8000/docs
```

**No keys?** Set `PROVIDER_FIXTURE_MODE=true`. The providers then return deterministic data:
- Hashes or URLs containing `bad` are reported as malicious.
- Hashes starting with `0000` are reported as "not found".

Every result is stored and returned with `"fixture": true`. Fixture mode refuses to start when `APP_ENV=production`.

## Docker (PostgreSQL)

```bash
cp .env.example .env    # set POSTGRES_PASSWORD and keys
docker compose up --build
```

The container runs `alembic upgrade head` before starting. The API binds to `127.0.0.1:8000`. Put a TLS-terminating reverse proxy (nginx, Caddy, or a cloud load balancer) in front of it for production.

> In the development environment this was written in, there was no Docker runtime, so `docker compose up` **has not been executed**. Everything else below was run.

## Tests

```bash
.venv/bin/python -m pytest -q
```

All external APIs are mocked with `httpx.MockTransport`. No real detections are used in tests.

## API (v1)

All `/api/v1` endpoints except `/auth/register` require `Authorization: Bearer <token>`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/register` | Register a device. Returns a token once; only its SHA-256 is stored. Optionally gated by `REGISTRATION_SECRET`. |
| POST | `/api/v1/scans/apk` | **Preferred.** Submit the SHA-256 and the metadata extracted on the device. The file never leaves the phone. Returns 202 and a `scan_id`. |
| POST | `/api/v1/scans/apk/upload` | Multipart APK upload for server-side hashing and ZIP checks. The file is forwarded to VirusTotal only when the hash is unknown **and** `consent_upload_to_virustotal=true`. The temp file is always deleted. |
| POST | `/api/v1/scans/url` | URL reputation scan. The URL is **never fetched**. `submit_if_unknown` asks VirusTotal to analyse it. |
| GET | `/api/v1/scans/{id}` | Poll a scan (`queued` / `running` / `completed` / `failed` / `canceled`). |
| POST | `/api/v1/scans/{id}/cancel` | Cancel a running scan. |
| GET | `/api/v1/history?limit&offset` | The caller's own scans. |
| DELETE | `/api/v1/history/{id}` / `/api/v1/history` | Delete one scan or all of the caller's scans. |
| GET | `/api/v1/providers/status` | Which providers are configured, plus licensing notes. |
| GET | `/health`, `/ready` | Liveness, and readiness (checks the database). |

The full schema is at `/openapi.json`. A snapshot is in `../ApkQoriqchi/docs/openapi.json`.

### Risk levels (`app/risk/policy.py`)

| Level | Meaning |
|---|---|
| `LOW` | No significant threat detected by the checks performed. This never means "safe". |
| `MEDIUM` | Suspicious indicators, or 1–3 engine detections that could be false positives. |
| `HIGH` | Strong evidence: 5 or more engines, 3 or more engines with a threat label, a reputation-list match, or a changed signing certificate. |
| `CRITICAL` | 10 or more engines, or an independent reputation list and VirusTotal agree. |
| `UNKNOWN` | Missing reports, provider failures, or too few engines. A missing report is **never** turned into a clean result. |

Heuristics on their own (permissions, domain shape) are capped at `MEDIUM`. The AI analyst cannot change the level: `ground()` forces its `risk_level` to the policy verdict and removes any detections that are not in the evidence.

## Design notes

- **No Redis or queue.** Scans run as in-process asyncio tasks, because one scan is a few outbound HTTP calls plus optional polling. The rate limiter is also in-memory. For more than one instance, move jobs to a queue (RQ, Celery or Arq on Redis) and the limiter to Redis.
- **Resilience.** Bounded retries with exponential backoff on 5xx and timeouts, a per-provider circuit breaker (opens after 5 consecutive failures, 60 s cooldown), and quota (429) responses surfaced as `quota_exceeded`.
- **Logs.** Structured JSON logs with a correlation ID (`x-request-id`). API keys, `?key=` query values and bearer tokens are redacted, and `httpx` request logging is silenced because Safe Browsing puts the key in the URL.
- **Privacy.** History stores the minimized URL only (`scheme://host/path`, with no query, fragment or credentials). Raw APK bytes are never stored.
- **Licensing.** The Safe Browsing API is for non-commercial use. Set `URL_REPUTATION_PROVIDER=webrisk` and `WEB_RISK_API_KEY` for commercial deployments. The VirusTotal public API (4 requests/min, 500/day) is also non-commercial; production use needs a VirusTotal premium licence.
- **Secrets.** Read from environment variables only. `.env` is git-ignored, and `.env.example` contains placeholders only. In production, use your platform's secret manager (Docker or Kubernetes secrets, AWS Secrets Manager, GCP Secret Manager).
