# Candidate 1.0.1 / build 2

Channel: register explicitly against the actual deployment. Status: candidate, NO-GO pending build/device evidence.

## Tester notes

Settings now includes Beta feedback & release status. Invited testers can submit categorized reports, optionally include a search-quality assessment, read release notices and request removal or analytics opt-out. Android reports safe version/device-family context to help identify affected builds. No private media is attached automatically. Screenshots and diagnostic file uploads are not enabled.

Test sign-in with your invited account, returning sessions, photo search, Memory Pass/Usage accounting and Save As Copy. Report unexpected results and video timestamps explicitly. Advanced Studio capabilities remain limited; no new editing or search features are claimed.

## Internal notes

Introduces a separate operations store and persistent server channel binding. Use a new ledger path for each environment/channel; startup rejects channel mismatch. Production BETA needs verified-email invitations. BETA_REQUIRED applies at auth and every account operation, with session revocation on pause/removal. Existing offline local operations cannot be remotely recalled.

Android native package version is bumped from 1.0/build 1 to 1.0.1/build 2. Adds feedback navigation, foreground update notice, safe account-request metadata and online editor-entry authorization. Existing trusted native completion-provider and device auth validation remain blockers. No ranking, pass pricing, free allowance, model cost, holdout or advertising changes.

Do not publish these internal security/deployment details as tester release notes.
