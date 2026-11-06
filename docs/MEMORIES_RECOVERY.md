# Recover library reads in Memories — November 6

0 new features, 1 core read/usability fix, 0 scaffolding. Existing browsing is
improved; source workflows remain 83, batch progress 45 beyond baseline 38.

Memories previously ignored provider loading and error state. A failed first-page
read could look like an empty library, while Load more had no pending feedback.
The screen now distinguishes loading, failed reads and no matches among loaded
items. It shows the provider error with a readonly Retry library action, preserves
available sections, and explains the loaded-only scope of filename filters.

Paging has progress/disabled feedback and shares a synchronous operation guard
with retry and pull-to-refresh. A refresh clears the viewer and reloads current
metadata; filename/type choices remain. Retry reloads the first library page,
rather than restarting indexing. Further pages remain available through Load
more. Indexing disables page admission, matching the provider's existing policy.
Unmount invalidates screen callbacks; it does not cancel native reads or suppress
valid provider updates for other screens.

Seven new tests render actual Memories state/section logic with explicit mocks
for grid, viewer, search/header and library boundaries. They check error versus
empty state, loading feedback, same-tick admission, viewer clearing, query/type
retention, retry, stale callback refusal, loaded filename matches on another page,
the selected original URI and indexing admission.

All 195 Jest tests in 19 suites pass. Typecheck exits 0. Lint exits 0 with 0 errors
and 37 warnings. Logs and current source/test hashes are retained under
.context/memories-recovery. No application dependency, native bridge, search/index
algorithm, credit/ledger or upload behavior changed.

These checks do not exercise native pull gestures, keyboard handling, actual
grid/viewer rendering, media providers, large-library memory/scroll performance,
or Android/device behavior. No screenshot or pixel acceptance is claimed here.
No full native/Gradle/APK build, packaged drawing refresh or Seran holdout/expensive
benchmark was run; those remain separate from this source milestone.
