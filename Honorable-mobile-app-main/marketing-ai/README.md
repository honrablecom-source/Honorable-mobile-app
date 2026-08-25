# Honorable Marketing AI

A separate pre-launch marketing system with safe local defaults and opt-in production integrations. It does not import or modify Memories AI, TinyCLIP, VLM search, iOS search, subscriptions, or authentication.

## Run

```bash
cd marketing-ai
cp .env.example .env # optional; the defaults work without it
npm start
```

- Waitlist website: `http://127.0.0.1:4173/`
- Marketing dashboard: `http://127.0.0.1:4173/dashboard`

Run tests with `npm test`.

## Customer discovery

Run one real scan across every configured source:

```bash
npm run discover
npm run discover -- --query "can't find old photos"
npm run discovery
```

The command prints and stores only results returned by configured live sources. Source failures are isolated, results are deduplicated and ranked, and no mock opportunity is substituted into CLI output. The dashboard at `/dashboard` provides the same scan plus `OPEN`, `GENERATE RESPONSE`, `EDIT`, `APPROVE`, and `REJECT` controls. Approval only marks copy ready for manual posting.

The system keeps four boundaries explicit: customer discovery finds public problem signals; content marketing creates Honorable-owned campaigns; account management concerns authorized official accounts; auto-reply reviews qualified discoveries. YouTube is secondary for customer discovery and remains primarily a content-marketing channel. WhatsApp private messages, groups, contacts, and conversations are never searched.

With `ZERO_COST_MODE=true` (the default), topic search tries an existing Google Programmable Search configuration first, then an explicitly configured self-hosted SearXNG JSON endpoint. Paid providers, including Brave Search, are skipped even if credentials are present. Discovery also aggregates configured public RSS/Atom feeds (`RSS_FEED_URLS`, comma-separated), Reddit's public search RSS, the Hacker News public search API, and enabled official free-quota connectors. X, Facebook, Instagram, and LinkedIn remain `PUBLIC_WEB` or `MANUAL_ASSIST` unless an eligible official connector is configured. SearXNG has no bundled public-instance default; configure only upstream engines whose terms permit this use. `npm run discovery` reports provider health without fabricating results.

For optional scheduling, invoke `npm run discover` from the deployment platform's scheduler every six hours. Do not overlap runs, and reduce frequency if a configured API approaches its free quota.

## Controlled auto-reply

Open `/dashboard/social/autoreply` or run `npm run autoreply:dry-run`. The default is `APPROVAL_REQUIRED` with `AUTO_REPLY_DRY_RUN=true`, global auto-reply off, conservative limits (2/hour and 6/day), and no live posting adapter. A platform cannot become eligible until its official reply API, official Honorable account OAuth authorization, and permissions are verified. The kill switch immediately disables the global queue.

## Production configuration

- Set `WAITLIST_STORE=postgres` and `DATABASE_URL` for the production PostgreSQL repository. Tables and unique email constraints are initialized automatically. Local JSON is development fallback only.
- Development email can use Resend's `onboarding@resend.dev` sender by setting the existing `RESEND_API_KEY` and `RESEND_DEV_RECIPIENT` to the verified Resend account email. This address is the only permitted development test recipient and is never taken from waitlist input.
- Leave `EMAIL_PROVIDER=disabled` while no production domain exists. Waitlist signups and consent are still stored successfully and welcome delivery is recorded as `skipped`.
- Later, set `EMAIL_PROVIDER=resend`, `EMAIL_FROM` on a verified domain, and an externally reachable `PUBLIC_BASE_URL` to enable production welcome delivery.
- Set `DISCOVERY_MODE=live` plus official provider credentials. Collection occurs only when an operator presses **Refresh official APIs**; it never posts, comments, messages, or downloads media.
- Never commit `.env`. Use the deployment platform's encrypted secret storage.

See [PLATFORM-COMPLIANCE.md](./PLATFORM-COMPLIANCE.md) for permissions, approvals, and quota notes.

## Safety and connector policy

- All bundled opportunities are explicitly labeled mock data.
- Platform adapters do not claim `CONNECTED` unless required credentials and approvals are configured. A refresh error is displayed without exposing secrets.
- No scraping, automatic DMs, comment spam, email harvesting, fingerprinting, or platform bypasses exist.
- Discovery stores the public URL, public excerpt, problem signals, and aggregate engagement only; it does not build personal profiles or collect contact details.
- Content generation creates drafts only. Approval marks a draft ready for manual publishing; it never publishes.
- `resend.dev` delivery is isolated from waitlist delivery and recipient-locked. Production Resend delivery is available only when explicitly selected with a verified-domain sender.
- Development waitlist state is stored in `data/marketing-state.json` with mode `0600` and ignored by Git. Production uses PostgreSQL.
- API secrets belong in environment variables; `.env` is ignored.

The current content generator uses deterministic local rules, so no paid AI API is required. `MarketingLanguageModel` can be replaced later without changing campaign workflows.
