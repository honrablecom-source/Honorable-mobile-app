# Review before replacing a drawing — November 5

0 new features, 1 core correctness/usability fix, 0 scaffolding. Source workflows
remain 82, batch progress 44 beyond the baseline of 38.

Named-project Open, chosen-file Open and project JSON Import previously replaced
the current canvas and its local draft immediately. Undo could restore it only
within the current editor session. Nonempty drawings now require a review with
Keep current drawing and Replace current drawing. The message explains how to keep
both projects first and the limits of session Undo. Empty canvases open directly.

Graph validation happens before review. Approval rechecks an exact current-scene
snapshot; if the artwork changed, it refuses and asks the user to review again.
The asynchronous photo-check path also rechecks that snapshot before publishing.
Closing or changing the workspace panel, or switching project tasks, dismisses the
review. The review heading receives keyboard focus; cancellation returns focus to
the project task heading. Starting an open review dismisses a shelf replacement
review and starting a shelf save dismisses an open review, keeping one decision
visible at a time.

Evidence in .context/project-open-review:

- Before browser test reproduces the original draft being replaced by the named
  project before review. No Android Activity is executed by this test.
- After browser checks cancellation, task-switch dismissal, changed-artwork
  refusal using actual Undo, explicit replacement, one-step Undo, reopen, unchanged
  named shelf and direct opening into an empty workspace. Array order and artwork
  fields survive Undo; engine revision stamps and fractional ordering indices may
  be regenerated. Review layout passes 320/390/844px overflow checks; the 390px
  screenshot was visually reviewed. No page errors or external requests occur.
- All 167 drawing Node tests pass. Existing editor, photo recovery, project-file
  and graph-validation browser regressions pass with explicit approval added only
  to successful replacement actions; rejection and preservation assertions remain.
- Isolated web compilation succeeds with the existing missing React Native
  TypeScript base-config warning. Source and 266 generated files match.

This is browser/source evidence with synthetic artwork and simulated native file
callbacks. No packaged Android assets were refreshed, no full native/APK build ran,
and actual Android WebView, provider, lifecycle, keyboard/accessibility and device
acceptance remain pending. Existing source media/search/billing/privacy policy and
native bridge schema are unchanged. The broad batch-build cadence remains intact.

## Follow-up: invalidate approval when incoming data changes

0 new features, 1 follow-up correctness fix, 0 scaffolding. A review previously
remained visible after editing incoming JSON, exporting different JSON into the
same field, or staging a newly chosen file. Its approval still referenced the old
project. Those three actions now dismiss the review without changing the canvas.
Open/Import must be selected again to review the latest incoming project.

The expanded actual browser fixture reproduces stale review after a JSON edit on
the previous source, then passes all three incoming-source changes, latest-project
naming, exact current-draft preservation and previous cancel/stale-current/Undo/
reopen/empty-opening checks. Current editor and project-file browser regressions
also pass, as do all 167 Node tests and the isolated source/266-asset fingerprint.
Evidence and hashes: .context/open-source-change. Only synthetic local chooser
files are used; native providers, lifecycle, devices and packaged builds remain
unverified. The source workflow count remains 82, batch progress 44.
