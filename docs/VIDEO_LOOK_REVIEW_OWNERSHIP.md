# Saved video look approval

0 new user-facing features; 1 correctness fix; 0 scaffolding. Shared count remains 99/500 source workflows, 61 beyond the 38-workflow batch baseline. This fix has focused host/API evidence, no full native/APK build or real-device acceptance.

Saved looks previously used untracked dialogs with partial project/session comparisons. A callback could outlive its visible stage. Applying a previously listed look also did not reload the selected recipe.

`VideoLookReview` now leases exactly one stage. Opening its successor precedes dismissal of the predecessor. Late dismissal cannot close that successor. Save, delete and apply consume approval before validating/executing. A synchronous execution permit keeps existing store guards valid only within that action; failure, completion or lifecycle closure removes the permit. The Activity additionally requires exact dialog/request identity, visible tracked membership and the full existing project/source/duration/range/private-session/generation snapshot. Background entry returns before storage or widgets.

The save form validates its name and current catalog limits before consuming approval, allowing normal corrections. The actual store still reads the latest catalog and merges under its existing lock. A concurrent duplicate or IO failure after approval requires reopening. Delete retains the existing exact-record removal check. Apply reloads the catalog and requires matching name and grade, allowing unrelated additions. Identical delete/readd is intentionally indistinguishable. The check and project edit are not one cross-store transaction. Original model/codec/store sources are byte-identical to HEAD at validation.

Only the selected clip's grade changes, through the existing project draft path and Undo. Successful project mutation and preview failure have separate messages. The private name input clears on dismissal.

## Validation

- 495 host tests pass: 14 new and 481 retained. New cases exercise stage succession, late dismissal, one-use approval, failure cleanup, guard lifetime/revocation, stale full snapshots, foreign owners, hidden/untracked dialogs, name prevalidation, fresh-record checks, codec/catalog preservation and grade-only project Undo.
- Current Activity, new lease and test compile against pinned Android API34 references and the hash-verified retained dependency graph. This is not configured SDK37 or whole-module compilation. Only existing deprecated Android APIs produced warnings.
- 18 source checks verify entry ordering, tracked stages, guards, cleanup, preview reporting, lifecycle invalidation, and unchanged store/model/codec.
- Evidence: `.context/noble-fast-look-review/verification.json`, `tests.log`, `compile.log`, `wiring.json`, `exit.json`. The first interactive session vanished before producing tests; no compiler remained, so one detached retry was started and completed with exit 0.

The Activity fixtures allocate metadata-only objects without Android constructors and substitute side-effect actions. They execute the actual admission/approval functions and project mutations. They do not execute real dialog construction, Android automatic dismissal or private input cleanup, accessibility, AtomicFile/JNI/provider durability, rendering, encoded output, physical lifecycle or device behavior. Those require later integration/device checks. No full native/Gradle/APK build, dependencies, media upload, benchmark, ranking or ledger changes.
