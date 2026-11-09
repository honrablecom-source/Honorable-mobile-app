# Video AI entry admission

The video editor now checks its existing foreground, idle, recovery and source/project admission before granting access to local AI tools. Previously the shared helper checked only the access mode; a queued click could proceed while the editor was paused or recovery was unresolved.

The guard covers entry to scene suggestion, video-look selection, look analysis, rough-cut selection and rough-cut analysis. Both account and guest modes retain their existing on-device permission. Refusal returns before status widgets, selection controls, analysis flags, cancellation state, media decoding or worker creation. It does not close pending reviews, change the model or create Undo history.

This is **0 new features, 1 entry-admission fix**. The feature register remains unchanged. Existing inference, label scores, sampling, rough-cut assembly and asynchronous result callbacks are unchanged. This fix does not establish that every later analysis result is safe across lifecycle changes; that remains a separate review scope.

## Validation

Evidence is recorded under `.context/noble-fast-ai-entry-admission/`. The new constructor-free host tests cover both access modes, paused/busy/recovery/missing source or project/destroyed/finishing sessions, all five concrete entry points, preservation of pending approval and cancellation state, and admission after foreground/recovery restoration. Their failure against the original retained Activity is checked before acceptance against the changed Activity.

The source check removes the one added guard and requires exact equality with the original Activity, proving the rest of the file is unchanged. All five AI entry bodies must begin with the shared helper. The changed Activity and new tests compile against the pinned API34 reference graph. **625 focused host tests pass: 5 new plus 620 retained.** Against the original Activity, 4 of the 5 new tests fail; they all pass with the guard. Eight scoped source checks pass. Current hashes and results are recorded in `verification.json` and `wiring.json`.

These tests allocate Activity metadata without constructing Android widgets. They do not execute model inference, MediaMetadataRetriever, Android UI, real worker scheduling, physical lifecycle transitions, exports or a device. The API34 reference compile is separate from the configured SDK37 full module build. No whole-app/Gradle/APK build is performed under the owner's batch-build instruction. No dependency, media upload, search ranking, holdout, credit or ledger changes are involved.
