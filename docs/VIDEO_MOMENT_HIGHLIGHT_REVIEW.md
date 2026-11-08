# Saved-note highlight approval

0 newly added user-facing features; 1 correctness fix; 0 scaffolding. Focused host/API34 validation passes; actual Android UI, full native/APK and real-device acceptance remain unverified.

Highlights from saved notes previously kept an untracked editor open underneath an untracked confirmation. Its partial value checks did not establish that the confirmation still owned the visible review or the foreground project/source session. A successful edit and subsequent preview failure also shared one failure path.

The flow now captures the existing full project/source/duration/range/private-session/generation review before storage or widgets. Its editor and confirmation use one current batch stage and immutable proposal. Review snapshots the typed handles and selected moments before dismissing the editor. Back explicitly opens a new editor with those values; late dismissal of the old stage cannot close the successor, and its old proposal is cleared. Invalid handles, no selection and a whole-clip no-op stay in the editor without project mutation. Dismissal clears the handle inputs and checks.

Approval consumes its stage before reading the current note catalog. Exact dialog/request identity and the full review are checked before and after that read. The entire catalog must equal the reviewed catalog, retaining the original conservative behavior for added, removed or renamed notes and changed duration. A failed read or changed catalog consumes approval and keeps the timeline unchanged. The check is data equality, not a transaction locking the notes file and project together; an identical delete/readd is indistinguishable.

The existing highlight algorithm, source-time handles/merging, metadata and caption intersection behavior, note store and codec remain byte-identical. The existing project transaction preserves soundtrack settings and provides one Undo. Successful application is reported separately from failed preview. Saved notes and original media are unchanged.

## Validation

- 521 focused host tests pass: 10 new and 511 retained. The new cases execute actual Activity approval functions and project state: fresh catalog/one-use/Undo; renamed/removed/added/duration-changed catalog refusal; stale foreground/source/duration/selection/generation/session/recovery/busy state before reading; source changes during read; read failures; older/hidden/untracked confirmations; stage transitions used by Back; invalid input staying editable; lifecycle closure and paused entry before storage/widgets.
- Current Activity and tests compile against pinned API34 references and the hash-verified retained graph. Existing deprecated Android APIs produce warnings. This is not configured SDK37 or whole-module validation.
- 24 source checks verify captured ownership, tracked stages, preserved Back values, private input cleanup, proposal freezing, catalog validation inside consumed approval, guards before/after read, accurate preview reporting, and unchanged note/highlight/project/codec/review implementations.
- Evidence: `.context/noble-fast-moment-highlight-review/verification.json`, `tests.log`, `compile.log`, `wiring.json`, `exit.json`.

Fixtures allocate constructor-free metadata objects and substitute the catalog-read callback. They do not execute actual Android dialog construction/automatic dismissal/Back input restoration/cleanup/accessibility, AtomicFile/JNI/provider reads, physical lifecycle, preview/rendered/encoded pixels or device behavior. Those remain integration/device checks. No full native/Gradle/APK build, dependencies, uploads, benchmark, ranking or ledger changes. The shared-note claim was rejected while noble-main published C102; those notes stayed untouched until its explicit release.
