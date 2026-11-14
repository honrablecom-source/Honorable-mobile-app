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

Wrangler4.162.0 builds the bundle. Four new tests pass using real SQLite and Miniflare4.20260730.0/workerd with the configured compatibility flags/date. Synthetic RS256 Google fixtures cover wrong audience, durable identity/grants/session recovery across two runtime restarts, refresh replay revocation, V3 lock and rejection of unverified completion without debit. SQLite cases cover injected rename rollback, reopen, previous-value preservation, capacity failure, path isolation and attachment operations. All54 original backend tests also pass. These tests do not establish live Google or physical Android acceptance and use no private media or Android VM/emulator.

For manual local review, put the existing public audience in ignored .dev.vars and run pnpm dev; keep the local port private.

## Deployment

Verify Workers Free first. Standard Wrangler login requests account:read, user:read and workers:write, plus required background access. The owner must approve this new access at action time; no token needs to be copied into chat. Inspect existing services before deploying noble-account so no existing deployment is overwritten blindly. Use pnpm deploy, then Wrangler's secret input to supply only the existing public audience without displaying it. Record the returned exact HTTPS address and probe JSON health/readiness. Readiness remains503 until trusted completion exists. Do not enable billing, paid Workers, domain purchases or paid services.

## Limits

This initial adapter retains existing JSON record formats and caps each record at1MiB. A storage/capacity fault returns503 and discards in-memory state; it never silently truncates or evicts balances. Large ledgers, sessions and analytics require a normalized schema migration before scaling. The single authoritative object supports a bounded initial deployment; scalability, backup/restore and load acceptance are not established. Free-tier exhaustion can interrupt service.

Primary references: [SQLite transactions](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/), [Node HTTP integration](https://developers.cloudflare.com/workers/runtime-apis/nodejs/http/), [Free-plan Durable Objects](https://developers.cloudflare.com/durable-objects/platform/pricing/).
