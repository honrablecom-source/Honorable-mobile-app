# Rough-cut proposal approval

The native rough-cut workflow now keeps the editor's full review snapshot through the target/focus chooser, analysis completion and Apply draft. A stale chooser cannot start analysis. A returned proposal must still match the foreground private session, access mode, generation, source URIs/durations, project/range, selected clip and soundtrack before opening its result.

Apply requires the exact tracked visible inspector and original selected clip, then consumes successful or unchanged approval. A replaced, hidden or stale callback cannot change the timeline or consume a successor. Undo cannot revive a consumed approval. An unchanged draft creates no history entry. Successful application is reported separately from unavailable preview. Progress messages require the same foreground project, mode and generation while analysis remains active.

**0 new features, 1 correctness fix.** Sampling, scores, deadlines, draft assembly, model validation and render code are unchanged. The worker body differs only in its progress-message predicate. Scene-suggestion results remain a separate review scope.

## Evidence and limits

**639 focused host tests pass:7 new plus632 retained**, with current Activity/test API34 reference compilation and6 source checks. Completed results and hashes are recorded in `.context/noble-fast-rough-cut-review/verification.json`. Seven new constructor-free host tests cover actual canonical replacement/settings/soundtrack/Undo/codec, consumed approvals after Undo, successor ownership, stale session/source/recovery/generation, hidden/untracked/changed-music/foreign-project refusal, unchanged-draft history and progress freshness. Six source checks require unchanged code outside rough cut and unchanged worker computation after normalizing the single progress predicate.

The tests do not execute Android dialogs, model inference, media decoding, real worker scheduling, physical lifecycle transitions, rendering, accessibility or device behavior. API34 reference compilation is distinct from configured SDK37/full native acceptance. No whole-app/Gradle/APK build is performed. No dependencies, personal uploads, benchmark/holdout, native search ranking, credit catalog or server-ledger changes are involved.
