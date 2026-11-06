# Studio keeps the exact chosen source

**0 newly added user-facing features; 1 correctness fix; 0 scaffolding.** Shared source register87/500 and batch49 remain unchanged. This is a source-level fix, not device or native-build acceptance.

Previously, a retained Studio source callback could open an original after it was removed, replaced under its media ID, hidden by a filename filter, moved off the visible page, or switched out of the current workspace type. Source actions also remained callable while the library was loading. The inspector stored only a media ID, so a library refresh could silently substitute a different original with the same ID.

Studio now retains the chosen source and checks its exact media ID, original URI and media type against the latest committed visible source page before editing or inspecting it. Loading and current import state also block stale source actions; existing same-frame pending, canvas and unmount guards remain. An invalid inspector source stays unavailable and asks the user to close details and choose again. It never automatically substitutes a replacement or first item. A deliberate new selection opens the replacement normally. Refreshing equivalent source records does not prevent editing.

## Evidence and limits

Eight new actual-screen regressions fail on the unchanged source (8 failed,28 passed), then pass with the fix. They cover removal, changed URI/type, filename filtering, mode switching, library loading, page changes/current refreshed identity and inspector replacement/reselection. The initial cold unchanged-source run also timed out in one existing5-second setup hook; the unchanged warm run isolates all8 reproducible failures without changing timeouts.

Final validation: **266 Jest tests/24 suites pass**, typecheck exits0, lint exits0 with0errors/38warnings. The tests render the actual Studio component with mocked library/native boundaries. Full logs and source hashes are retained in `.context/studio-source-identity/`. The first full run exposed an invalid inspector callback dismissing details; the final handler rejects invalid source identity before dismissing, and all266checks pass.

No new app dependency, permission, personal-media upload, search ranking, credit or server-ledger change. No benchmark/holdout, full app/native/Gradle/APK build or device validation. URI/type equality cannot prove unchanged file bytes or provider permission; actual editor launch, provider access, device touch/lifecycle and rendered native accessibility remain unverified. Already-started native work is not cancelled by this JS admission check. Same-frame view changes before a React commit are outside the latest-committed-view assertion.

A separate audit corrected the prior Library-to-video milestone's inaccurate footer-fix claim. Proposed background/layer styles produced zero changed pixels in controlled844x390 screenshots; all three viewport checks and badge/footer hit probes passed before and after. Those unnecessary edits were removed. Library and preview source remain unchanged. Retained images/logs/pixel comparison are in `.context/library-footer-layering/`; this adds0layout fixes or features.
