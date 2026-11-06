# Native drawing reset ownership

November 6: **0 new user-facing features; 1 correctness fix; 0 scaffolding. Source 93/500, batch 55 unchanged.**

New drawing now opens one foreground-owned confirmation. Pause/destroy invalidates and dismisses an unapproved review; a retained or repeated approval cannot clear the drawing after resume. Import/export beginning during review prevents approval. A stale review cannot replace current status with a misleading refusal notice.

An already approved reset is a different state: the renderer may already have removed the local draft and frozen the old canvas. Native therefore owns exactly one WebView dispatch and reply. A reply received while paused waits for foreground completion; it does not reload in the background or abandon a committed reset. Competing native import/export/reset admission stays closed until that matching reply is completed. Duplicate/older replies cannot reload again or release a later reset. Failure to dispatch releases only its own reset lease; destroy drops it. The unchanged renderer guard still refuses busy operations/storage-removal failures, prevents delayed autosave from resurrecting cleared work, and preserves named designs/components.

## Verified evidence

Five of six initial new constructor-free Activity regression cases failed against the original source: paused and closed entry, paused success reload, duplicate success reload and overlapping dispatch. Original 38 retained cases passed. Final 57 focused host tests pass: 19 new lifecycle/identity/dispatch families plus 38 retained drawing policy/project/reset/print/SVG cases. Eight current production sources compile against Android API 34 and retained surrounding photo APIs; this is not full configured SDK37 module compilation. The five retained reset admission tests now explicitly initialize foreground and the Activity lock, with their assertions unchanged.

Actual Excalidraw browser regression passes using the exact current native reset script: busy file-save refusal, storage removal failure preserves the draft, old-page lifecycle/timer/late callback cannot rewrite cleared work, blank reopen and intact named/component shelves. Zero page/external-request errors. Screenshot inspected. Browser fixture accepts an isolated output directory so earlier evidence is not overwritten. No app JavaScript changed.

Logs, tool hashes, current source hashes and browser artifacts are local under `.context/drawing-reset-lifecycle/`: `before-jvm.log`, `before-compile.log`, `compile.log`, `jvm.log`, `native-source-hashes.json`, `browser.log` and `browser/review.json`. Intermediate work is retained; final evidence is distinguished in the verification manifest. No app dependency upgrades, benchmark, personal-media upload, search/ranking/billing change or full native/APK build. Packaged drawing assets stay deferred to approximately 138 source workflows.

## Remaining acceptance

Host tests allocate the actual Activity and a recording WebView without Android constructors. They execute real private admission, review tokens, callbacks and flags, but do not execute an Android dialog, actual pause/resume/destroy, WebView scheduling, rotation/process death, accessibility or device rendering. The browser simulates native callback timing. At the agreed build batch, test cancel/pause/resume before approval, approved reset with delayed WebView reply, quick background/foreground, storage failure and recreation on a real device. Confirm one blank reopen, current operations remain owned, and named designs/components survive. This fix does not establish device or store readiness.
