# Noble account Worker

This adapter runs the existing production account routes with SQLite-backed Durable Object storage. It is prepared for the owner's Workers Free account. Deployment, live probes and real Google sign-in are separate acceptance gates.

The Ledger, PersistentSessions, BetaStore, Attachments and AnalyticsStore accept an optional storage adapter; normal Node deployments retain their filesystem default. The Worker atomically replaces SQLite records and serializes account requests across asynchronous Google-key retrieval. One named object preserves cross-account identities, shared installation grants, receipt ownership and request-id deduplication. Only session hashes are persisted, never plaintext access/refresh tokens.

Production mode is fixed. Development/admin routes are unavailable, browser origins are empty and observability is disabled. The existing public Web audience is configured as HONORABLE_GOOGLE_WEB_CLIENT_ID; no Google client secret is needed. Never commit environment files or Cloudflare credentials.

Without an approved trusted completion provider, /readyz deliberately returns503/ready:false. Do not fake a verifier, charge client-reported success, enable development mode or treat audience configuration as readiness. /healthz should return200 with service honorable-account/status UP. The Android combined build still waits for production acceptance.

## Verification

Use Node24 or later, then from this directory:

```sh
pnpm install --frozen-lockfile --ignore-scripts
pnpm run build
pnpm test
```

Wrangler4.162.0 builds the bundle. Eleven focused tests pass using real SQLite and Miniflare4.20260730.0/workerd with the configured compatibility flags/date, plus the browser checker's session-cleanup and origin/body guards. Synthetic RS256 Google fixtures cover wrong audience, durable identity/grants/session recovery across two runtime restarts, refresh replay revocation, V3 lock and rejection before starting production searches without trusted completion. SQLite cases cover injected rename/chunk rollback, multi-megabyte reopen in actual workerd, legacy-record migration, corruption, capacity, path isolation and attachment operations. All54 original backend tests also pass. The test command builds the current bundle first. These tests do not establish live Google or physical Android acceptance and use no private media or Android VM/emulator.

For manual local review, put the existing public audience in ignored .dev.vars and run pnpm dev; keep the local port private.

## Deployment

Verify Workers Free first. Standard Wrangler login needs account:read, user:read and workers_scripts:write, plus required background access. The older workers:write scope was accepted by consent but rejected by the deployment API in the November14 rollout; use the specific script scope. The owner must approve new access at action time; no token needs to be copied into chat. Inspect existing services before deploying noble-account so no existing deployment is overwritten blindly. Use pnpm deploy, then Wrangler's secret input to supply only the existing public audience without displaying it. Record the returned exact HTTPS address and probe JSON health/readiness. Readiness remains503 until trusted completion exists. Do not enable billing, paid Workers, domain purchases or paid services.

## Limits

The adapter retains existing JSON record formats, storing up to16MiB per record in64KiB SQLite chunks with a SHA-256 checksum. Legacy records migrate atomically on their next write. A guard in the old table forces old adapters to fail closed if accidentally rolled back after migration; do not roll back to the original single-blob adapter without a verified data conversion. Interrupted updates preserve prior records. Missing, reordered or damaged chunks fail closed. A storage/capacity fault returns503 and discards in-memory state; balances are never silently truncated or evicted. The single authoritative object and JSON rewriting still require a normalized schema and load/backup/restore acceptance before scaling; this change is not unlimited capacity. Free-tier exhaustion can interrupt service.

## Owner's live Google check

From the repository root, run the helper with the private account env file supplied through Node's env-file option. The entry point is dev-server/cloudflare/tools/live-signin-check.mjs under Honorable-mobile-app-main. It binds only127.0.0.1:4174 and serves the existing authorized http://localhost:4174 origin. It uses Google's real GSI button, forwards the credential to the approved HTTPS service, checks the authenticated session and refresh rotation, verifies unchanged credit balances, then signs the temporary session out. Tokens stay in memory and are never printed, persisted or returned to the page. No installation grant, purchase, search, media or query is sent. Evidence contains only result flags when NOBLE_SIGNIN_EVIDENCE_PATH is explicitly set to an ignored/private path. The helper stops accepting connections after15minutes. This check does not establish Android sign-in or trusted search completion; the owner must complete the Google account picker.

Primary references: [SQLite transactions](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/), [Node HTTP integration](https://developers.cloudflare.com/workers/runtime-apis/nodejs/http/), [Free-plan Durable Objects](https://developers.cloudflare.com/durable-objects/platform/pricing/).
