# Library responses follow the current request — November 5

0 new features, 1 core correctness fix, 0 scaffolding. Source workflows remain 82,
batch progress 44 beyond the baseline of 38.

The React Native library provider previously applied every asynchronous result.
An older page could therefore repopulate items after a newer reload had cleared
the library or observed denied permission. Older status/error responses could
also replace newer state, and load-more failures escaped instead of producing
recoverable library feedback.

Reload and index operations now invalidate older request generations. Page,
status and error updates check both the active provider and their request owner.
Repeated page loads are admitted once; invalidation releases old admission so
new work is not blocked by a stale request. Index status polling keeps one native
request in flight and discards obsolete responses. Confirmed permission loss also
clears displayed items and totals. Page failures become visible errors and permit
retry. Unmounting invalidates all requests; it does not cancel native indexing.

This change only controls React library metadata/state updates. It does not alter
the native on-device retrieval engine, ranking, indexing algorithm, permission
set, credit/ledger policy or upload behavior.

The original five provider tests reproduce late-page repopulation, stale reload
replacement, stale error feedback, duplicate page admission and an uncaught page
failure. The expanded deferred-promise/fake-timer suite also covers obsolete
polling, permission-loss clearing, unmounted callbacks, stale indexing errors
and slow status polls. These tests mount the actual LibraryProvider and mock its
native bridge; they do not prove Android permission or process lifecycle behavior.

Evidence is retained under .context/library-freshness. The first final-check
attempt stopped with missing tsc/eslint/jest after temporary dependencies vanished.
Dependencies are restored from the exact unchanged lockfile before rerunning the
current source checks. No full native/APK build, real-device test, expensive
benchmark or Seran holdout is part of this milestone.

Final validation on the current source: typecheck exits 0; lint exits 0 with
0 errors and 38 warnings; all 164 Jest tests in 18 suites pass, including 10 new
provider tests. The final poll test verifies that a slow response admits only one
native status request and subsequent polls resume after it settles. Cleanup
invalidates requests through a stable callback, without suppressing lint rules.
The dependency lock remains unchanged. Logs and current source/test hashes are
stored with the evidence above. These tests mock native responses; actual Android
permission changes, provider I/O, rendering and lifecycle acceptance remain open.
