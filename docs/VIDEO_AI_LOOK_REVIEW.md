# AI video-look approval

The native video-look workflow now carries the editor's full review snapshot through chooser selection, analysis completion and explicit Apply. The chooser is tracked; a stale chooser cannot start analysis. A late result must still match the foreground project, access mode, private owner, generation, source URIs and durations, selected clip, trim range and project state before opening its proposal.

The result uses the existing tracked inspector approval. Apply requires the exact visible current dialog and original clip identity, then consumes that approval. Replaced, hidden, paused, recovery-blocked or stale proposals cannot mutate the project or consume a newer inspector. Undo does not revive an already consumed approval. An unchanged grade creates no Undo entry. Successful color application is reported separately from unavailable preview.

This is **0 new features, 1 correctness fix**; it does not advance the feature register. The inference thread, sampled frames, label scoring, grade calculation, timeouts and cleanup remain byte-identical. Rough-cut and scene-suggestion result callbacks remain separate review scopes.

## Evidence and limits

**632 focused host tests pass: 7 new plus 625 retained.** The changed Activity and new tests compile against the pinned API34 reference graph. `.context/noble-fast-ai-look-review/verification.json` records completed results and source/dependency hashes. New constructor-free tests exercise actual Activity approval helpers and canonical project operations: selected-grade-only mutation, one Undo, draft codec, consumed approval after Undo, successor ownership, lifecycle/recovery/source/duration/mode/generation refusal, hidden or untracked dialogs, foreign project and changed clips, and no-op history. Six source checks prove unchanged code outside the look flow, identical inference worker bytes, and tracked chooser/result wiring.

These checks do not execute actual Android dialogs, inference, MediaMetadataRetriever, real worker scheduling, physical lifecycle transitions, rendering, accessibility or device behavior. API34 reference compilation does not establish configured SDK37/full module acceptance. Full native/app/APK builds remain deferred under the owner's batch instruction. No dependencies, personal uploads, search ranking, holdout or credit/ledger changes are involved.
