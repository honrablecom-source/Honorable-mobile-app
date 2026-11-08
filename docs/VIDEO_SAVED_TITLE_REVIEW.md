# Saved title appearance — Activity approval

0 newly added user-facing features; 1 correctness fix; 0 scaffolding. Source count remains 101/500, batch63 beyond38. Focused host/API34 tests pass; actual native UI/worker/storage/device and full APK acceptance remain unverified.

The saved-title shelf already checks private storage ownership on its worker, verifies the selected record before applying, and closes on Activity pause. Its Activity validity callback only compared project values, selected index and access mode. It omitted the full source URI/duration/private-owner/generation snapshot and exact shelf identity.

The Activity now captures its shared full review before pausing playback or constructing the shelf. `isCurrent` requires that exact shelf and review. UI busy updates and messages are also scoped to the current shelf. The existing worker releases its busy flag before checking UI validity; that order is unchanged, so this full admission check does not block normal completion merely because its own job was busy.

Apply returns immediately for a foreign shelf. Its own identity is consumed before full validation and existing project application. A stale own approval cannot revive after Undo. The selected/all scope copies only title appearance through the existing model; words, blank titles, captions, timing, media, other appearance, audio and soundtrack settings remain. Matching values consume approval without adding Undo history. Successful mutation and preview failure have separate messages.

This is the Activity boundary fix. `VideoTitleStyleShelf`, its dialog stages, worker scheduling/cancellation, private-owner/catalog checks, store, codec and project model are unchanged. Exact visible-widget stage ownership needs its own controller audit; this change does not claim to fix it.

## Validation

529 focused host tests pass: 8 new and 521 retained. New tests execute actual Activity approval/admission and project state for selected/all application, one Undo, no-op, stale foreground/source/duration/range/generation/session/recovery/busy/music refusal, old-owner isolation and Undo revival refusal, busy flag release compatibility, blank words/custom style codec recovery, and paused entry before shelf workers/widgets. Current Activity and tests compile against pinned API34 references and the hash-verified retained dependency graph. Existing deprecated Android APIs produce warnings; this is not SDK37 or whole-module compilation.

20 source checks verify capture order, captured mode, full exact-owner admission, owned busy/message callbacks, consumption before validation, unchanged model application, no-op history, separate preview reporting and owner assignment before open. They also confirm byte-identical shelf controller, store, title models, project and draft codec sources.

Evidence: `.context/noble-fast-saved-title-review/verification.json`, `compile.log`, `tests.log`, `wiring.json`, `exit.json`. Shelf objects in these tests are constructor-free identity tokens; tests do not execute actual controller jobs, UI widgets, executor scheduling, account/storage/AtomicFile/JNI/provider IO, physical lifecycle, preview/rendered/exported pixels or device behavior. Those remain integration/device checks. No full native/Gradle/APK build, dependencies, uploads, benchmark, ranking or ledger changes. Broad build remains approximately138 source outcomes.
