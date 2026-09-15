# Honorable private beta operations

The private console's **Beta** section manages invited testers, cohorts, bug reports, grouped errors, release evidence, rollout flags and aggregate exports. Consumer entry: **Settings → Beta feedback & release status**. No invitation emails are sent by this implementation.

## Local operation

Use the repository root. An existing admin setup continues to work:

```sh
npm run admin:setup  # only if no OWNER config exists
npm run admin:dev
```

Open **http://localhost:4174/admin**, sign in and select **Beta**. This default is DEVELOPMENT / DEV, with optional enrollment. Invite a test email before submitting feedback. In the invite-only local BETA launcher, “Use test account” asks for the email already invited by the operator. This test identity flow exists only in development. The isolated browser integration test also exercises invited development sign-in:

```sh
npm run test:beta
npm run test:beta:web
```

To run a separate invite-only BETA development sandbox, stop the existing gateway and run `npm run beta:dev`. It selects the dedicated `dev-server/data/beta-ledger.json` store automatically. The equivalent explicit command is:

```sh
HONORABLE_RELEASE_CHANNEL=BETA \
HONORABLE_LEDGER_PATH="$PWD/Honorable-mobile-app-main/dev-server/data/beta-ledger.json" \
npm run admin:dev
```

Do not reuse the same ledger/operations store across channels or environments. A persisted channel mismatch fails startup. Each server instance has one channel (DEV, INTERNAL, BETA or PRODUCTION), assigned by server configuration; request bodies cannot select telemetry channels. Environment remains separate: DEVELOPMENT BETA activity is not PRODUCTION BETA activity. `HONORABLE_BETA_REQUIRED=1` also enables invite enforcement outside the BETA channel. BETA always requires invites.

The bundled web shell remains a development tool and must not be used as the public beta service. Its development purchases are explicitly test-only. Real invited Android testing needs a private HTTPS account deployment configured with `HONORABLE_SERVER_MODE=production`, `HONORABLE_RELEASE_CHANNEL=BETA`, a separate ledger path, valid Google audience, production-scoped admin credentials and the trusted search completion verifier. Those deployment/device dependencies are not provisioned by this sprint. The existing native completion-verifier gap still prevents a GO decision.

## Tester and feedback flow

An OWNER/ADMIN records an email and cohort. Authentication accepts that invitation only after the Google credential is verified and `email_verified` is true; the invitation binds to the resulting server account ID. Development identity matching exists only in development. INVITED becomes ACTIVE on acceptance. PAUSED/REMOVED block server operations and revoke sessions. Cohort assignment alone never disables a feature.

Use `INVITE-TEMPLATE.md` for manual invitations and explain no production billing. Feedback has an idempotent event ID and a stable `HON-year-sequence` bug ID. Duplicate retries do not create duplicate bugs. Each tester can submit at most 20 reports/day. The lightweight store caps testers at 10,000 and feedback at 20,000, returning a capacity error rather than silently discarding reports.

Reports support categories, title, description, safe version/platform/screen metadata and optional explicit search-quality assessment. Web diagnostics can include current model, latency and rank positions only when the tester checks consent; raw query text and media paths are never automatically included. The API supports opaque `result-<hash>` references, not filenames. Native feedback currently supports the assessment without automatically attaching result diagnostics.

**Optional attachments require a deliberate user action and consent.** Android and web pick a screenshot, resize/re-encode it to PNG (maximum 512 KiB) and ask permission before sending. No automatic capture occurs. Safe diagnostic JSON uses the same strict metadata schema; arbitrary private diagnostic files are rejected. Each report allows three attachments, retries deduplicate identical content, and attachment content expires after 30 days. Files are stored with mode 0600 under opaque names. OWNER/ADMIN downloads are authenticated and audited; VIEWER cannot read attachments. Retention cleanup runs on attachment access/upload; an expired file cannot be downloaded. Feedback free text is deliberate tester content, restricted to OWNER/ADMIN support views. Aggregate export excludes reporter IDs, emails, descriptions, notes, attachments and internal release details.

Bug workflow: NEW → TRIAGED → IN_PROGRESS → FIXED → VERIFIED → CLOSED. Operators record severity S1–S4, assignee and notes. S1 remains a blocker while FIXED until VERIFIED/CLOSED. Errors are grouped by code/platform/version, with counts, affected users and first/last seen. The existing Errors page links to Beta triage. Errors are not relabeled as crashes; Android 11+ OS exit-reason reporting groups JVM/native crashes and ANRs on the next authenticated launch. It reads no stack traces or exception descriptions. Crash-free rates use only supported observed session IDs and are unavailable when reports cannot be reconciled to observed sessions. Older Android versions are not assumed crash-free.

## Flags and kill switches

Available flags: `studio_enabled`, `video_search_enabled`, `v3_visible`, `rig_preview_enabled`, `nodes_visible`, `code_workspace_visible`. Scope precedence: global **disable wins**, then ACCOUNT, COHORT, GLOBAL, default. V3 and rig preview default false. Existing model availability and entitlements remain independent requirements; a flag cannot unlock V3 or execute unsupported code/rig features.

V2 is enforced at server search start before reservation/debit. Editor entry checks server authorization on Android and web; web export and Code/Nodes entry also check it. Core photo editing retains its existing free access; premium workspaces retain Studio requirements. Disablement does not rewrite prices, grant credits or alter ranking. Already running local operations cannot be recalled after a network disconnect. Native editor initialization and Save As Copy reauthorize through the account service. Already running work cannot be recalled after it passed a check; do not claim universal remote control of offline code.

## Releases, evidence and marketing

Register immutable version/build/commit/channel identity, separate internal/tester notes and update policy; promote/withdraw via Change release status. OPTIONAL and RECOMMENDED communicate availability. REQUIRED also needs a minimum compatible build; the server blocks new search/editor authorization below that build (including missing build metadata), while feedback/account/logout remain accessible. Native displays update notices on startup/foreground; the web Settings entry shows release status. There is no unverified external download URL.

Checklists require explicit operator evidence for each item; they start unverified. Evidence is an audited operator attestation, not an automatic claim that CI/device tests ran. Each build starts with its own empty checklist. Critical open bugs/known issues or failed/missing core checks produce NO-GO. Other incomplete checklist items produce CONDITIONAL. All checks plus no critical bugs produce GO. See `RELEASE-CHECKLIST.md`.

MARKETING READY stays false until photo demo, Android build/artifact, auth, search ledger, editor save and privacy checks pass, with no critical open bugs/issues. It does not depend on unfinished advanced Studio features. Record approved screenshot/demo references and exact supported claims per release. The handoff is surfaced only when the marketing gate opens. Demo B remains false until separately approved with evidence; it is not implied by the photo demo.

No readiness evidence has been manufactured for this sprint. The prior native build succeeded but lacked configured Google/account sign-in and physical-device verification. Current operations gates therefore remain NO-GO / MARKETING READY FALSE until the operator verifies the new release.

## Privacy and storage

The account ledger remains financial authority. Operations are stored in a separate atomic mode-0600 JSON file beside it; admin reads/mutations use existing role, CSRF and append-only audit protection. Only the private admin route can mutate invitations, flags, issues, release records or bug triage. VIEWER receives aggregates and redacted tester data, not support email/report text/internal release notes. Do not publish a forwarding proxy to the loopback-only admin route.

Android sends package version/build, platform, OS version, manufacturer/model family and RAM class. There are no hardware serials, installation identifiers in the console, advertising IDs, media, scripts, tokens or queries in operations metadata. Vulkan support uses the reported Android system feature. RAM class is LOW below 3 GiB, MID below 6 GiB, otherwise HIGH; it is not a GPU benchmark. Bounded startup-to-authenticated-session, index and editor response timings, memory-pressure callbacks and low-memory preview proxy usage are collected without contents. Unmeasured fields stay unavailable. First install is the first observed authenticated beta activity, not an OS installation timestamp. Adoption percentages use testers with a known reported build and disclose unknowns.

Analytics opt-out immediately stops future optional account telemetry and tester observation. Existing history is handled by the separate retention/request process. Beta removal revokes access without deleting the account ledger. Account deletion opens a review request; marking that workflow fulfilled does **not** silently erase purchase records or implement legal retention policy. The operator must document actual fulfillment outside this automatic request tracker.

Detailed events retain existing analytics bounds; beta bugs, invitations and release evidence are operational records retained for review, not silently expired with telemetry. Single-process JSON is suitable for this controlled local beta tool. Multi-writer production storage, external immutable audit and production identity/network setup are separate deployment work.

## Coverage limitations

Implemented and tested: server beta access, invite acceptance, status/cohorts, feedback and consent schema, bug triage, grouped errors, release registry/policies, scoped flags, V2 kill switch, beta console/forms, adoption from observed versions, known issues, quality aggregates, privacy request workflow, conservative release/marketing gates and JSON/CSV summary export.

Remaining external validation: real-device behavior, controlled distribution, production Google/account configuration and the trusted native completion verifier. OS exit-reason capture is available on Android 11+ and only on a later authenticated launch; it is not a guarantee of capturing every fatal event. Arbitrary diagnostic files remain disallowed; optional diagnostic attachments contain allowlisted JSON only. Editor durations measure observed open-to-close sessions, and native startup measures application creation to authenticated beta restoration/sign-in, not a synthetic benchmark. Empty or unavailable metrics are labeled, never filled with synthetic customers or success rates. LOW_END_DEVICE and VIDEO_TESTERS are operational review cohorts; no holdout data or ranking changes are used.

## Verification commands

```sh
npm run test:product
npm run test:beta:web
npm run test:auth:web
npm run test:product:web
npm run typecheck
npm --prefix Honorable-mobile-app-main/mobile-react-native test -- --runInBand
npm run build:web
```

Browser evidence in `previews/beta.png` is isolated DEVELOPMENT / BETA activity. Search in that integration harness is an explicit fixture, while editor operations and account/feedback/admin requests execute the real code. It is not evidence of physical-device search quality or production customers. Prior real-engine evidence remains in `product-validation/` and must be reverified on the new Android candidate.

Android Actions now runs the full React Native and server/product suites and emits `honorable-release-identity` alongside `honorable-android-debug`. The identity manifest labels CI debug builds INTERNAL, records commit/version/build and only a boolean for sign-in configuration; it does not certify device testing or beta deployment readiness.
