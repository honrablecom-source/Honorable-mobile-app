# Private video moment-note controls

0 new user-facing features;1 correctness fix;0 scaffolding. Source101/500 and batch63 remain unchanged. Focused host/API34 checks pass; actual Android UI/storage/player/device and full APK acceptance remain unverified.

The note store already locks latest-read add/rename/remove operations. Its UI used untracked dialogs and partial project/account checks, including entry without the full foreground source/duration/generation snapshot. Old callbacks could outlive the visible stage. Jump also used a previously listed record without reloading it.

The flow now captures the shared full project/source/duration/range/private-session/generation review before player/storage/widgets. Its root menu, all-source list, action chooser, name form, deletion confirmation and About page use one tracked current stage. A successor owns its stage before the predecessor is dismissed. Old callbacks/dismissals cannot close or consume a successor or an independent saved-look owner.

Save/rename/delete/jump consume approval before guarded execution. Existing store callbacks remain valid only during that synchronous action and require the exact visible dialog/request/full review. Completion, failure, dismissal or lifecycle closure removes the permit. The highlights entry consumes its own action before opening the independently owned highlight tool. Normal label/duplicate-time prevalidation uses the latest catalog without writing, leaving the form editable on normal correction. A post-approval IO/concurrent-store failure requires reopening. Private label fields clear on dismissal.

Jump reloads and requires the chosen note data before seeking. Identical delete/readd is indistinguishable, and the record read and player action are not a cross-store transaction. Existing in-range jump checks, source-time position, labels/limits, store lock, filename/codec and add/rename/delete algorithms remain. Note actions do not add project Undo history or change rendered media. Cancellation is cooperative and does not promise rollback of committed storage.

`VideoActionReview` contains the previous look lease body verbatim; `VideoLookReview` retains its no-argument API and unchanged companion over that base. This reuses the existing synchronous action policy for notes. All prior look ownership tests run against the inherited implementation.

## Validation and limits

573 focused host tests pass:16 new plus557 retained. New tests execute actual Activity approval/refusal with substituted side-effect callbacks, pure stage/guard lifetime and note catalog operations. Cases cover stage transfer, late dismissal, one-use/failure cleanup, guard revocation, stale full snapshots, foreign/hidden/untracked owners, invalid labels/duplicate times remaining editable, latest catalog merge/rename/delete/codec, project/history preservation, changed/deleted note refusal before a substituted jump, cross-tool isolation and paused entry/lifecycle closure. Current Activity/shared lease/look API/tests compile against pinned API34 references and the hash-verified retained graph; existing deprecated Android APIs produce warnings.

27 source checks verify entry order, captured owner/source, all tracked stages, guards/consumption, label cleanup/prevalidation, current-record-before-seek, guarded store calls/highlights handoff, lifecycle closure, verbatim lease/companion preservation, and unchanged store/note/highlight/project/codec sources. Evidence: `.context/noble-fast-moment-note-controls-review/verification.json`, `compile.log`, `tests.log`, `wiring.json`, `exit.json`.

Fixtures use constructor-free Activity/Dialog/View metadata. They do not instantiate actual widgets, execute Android automatic dismissal/label cleanup/accessibility, perform AtomicFile/private-owner/provider IO, seek actual players, or prove physical lifecycle/rendering/device behavior. Those remain integration/device checks. No configured SDK37/full module/Gradle/native/APK build or readiness claim. No dependencies, personal media uploads, benchmark, ranking or ledger changes. Broad build stays approximately138 source outcomes.
