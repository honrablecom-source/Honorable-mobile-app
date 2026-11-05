# Review before restoring a saved video — November5

0 new features,1 usability fix,0 scaffolding outcomes. Shared source register remains83,batch45 beyond38. Experimental source/host/API validation; no full app build or device acceptance.

Guest Restore project and account Restore draft previously started replacing the current timeline immediately. Edited/restored timelines and pending range selections now ask Keep current or Restore saved first. An untouched original with its default range restores directly. This conservatively reuses VideoCloseState rather than querying saved shelves to compare exact versions.

The review explains replacement and session-only Undo. It suggests exporting a copy to keep the current rendered result; it does not suggest saving over the one-slot project/draft to keep both editable versions. A range only selected in the controls is not preserved by timeline Undo. Export copies remain rendered copies, not editable project backups.

Keep current performs no storage operation or timeline change; preview has been paused. Approval requires the currently owned dialog, foreground/idle state, unchanged access mode and exact captured project/source/duration/clips/music/selection/range/recovery state. Changed context refuses approval. Pause/destroy dismiss the dialog and remove ownership so old approval cannot run after return.

After approval, the existing guest/account read, owner/media validation and undoable apply paths run unchanged. Save operations are unchanged. Missing, damaged, inaccessible or conflicting saved data still follows those existing error/preservation checks. This review does not add named versions or additional save slots.

Validation:87 host tests and current Activity/shared API compilation pass. Three new constructor-free actual Activity families check paused guest/account restore admission, busy admission and approval invalidation after foreground/operation/mode/project changes. Existing VideoCloseState and restore/model/source-conflict tests remain among the84 retained cases. Production dialog and storage wiring is source-reviewed; the tests intentionally leave views and storage unavailable. Evidence `.context/noble-fast-restore-review/verification.json` includes exact current source/dependency/artifact hashes and full final logs.

No actual native review rendering/Back/cancellation, guest/account storage, provider/media validation, Android lifecycle/rotation, playback/export or device execution is proven here. API34 source compilation is not configured SDK37/full Gradle/native/APK integration, packaged assets, production or store readiness. Broad builds remain deferred under the owner's batch cadence. Main's Studio access work is separate and preserved.
