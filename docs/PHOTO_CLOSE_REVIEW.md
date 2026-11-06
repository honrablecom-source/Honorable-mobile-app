# Review before leaving the photo editor

Zero new features; one accidental-exit usability fix. The shared source register remains85/500, batch47 beyond38 at this milestone.

Native photo Close and system Back now share one exit path. It reviews current adjustments/objects/captions, Undo/Redo history, an unsaved image created in Noble's own creation provider, pending decoded session recovery and ongoing editing/save/export operations. An untouched original or identity saved canvas without history/work leaves directly. This is conservative session review: it does not infer whether a separate saved project or rendered export matches the current edit.

**Keep editing**, cancellation or Back on the review keeps the editor open. **Leave editor** requires the currently owned dialog plus the same edit/history membership, source, loaded scope/access mode, saved canvas ID, protected originals, foreground generation, preview revision/pixel identity, pending export/destination and operation phases. Changes while the review is open invalidate approval. Pause/destroy dismisses and invalidates it; a new review is needed on return. Immutable photo model values are retained by reference and history/original membership lists are copied.

The header Close listener deliberately remains available during a busy operation, showing the same warning as system Back. Leaving may interrupt work, and exports can leave empty/partial destination documents. A completed save can already exist even if its result callback is suppressed. Review neither deletes originals/saved projects nor guarantees operation cancellation, storage rollback, crash recovery or a durable backup. Saved edited projects may still need review because session Undo is separate.

Legacy Back uses Activity.onBackPressed. Android33+ platform callback types are isolated in a guarded nested helper, installed after UI creation and disabled/unregistered on destruction. Both existing PhotoEditorActivity manifest declarations explicitly enable the platform callback. No permissions or unrelated declarations change. If callback registration fails, the UI advises using Close; actual platform Back protection in that failure state is not promised. The callback intercepts Back for this Activity; predictive system animation behavior is not validated.

[Android's custom/predictive Back guidance](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture) documents the platform callback and activity-level opt-in used here. No new AndroidX dependency is introduced.

## Evidence scope

**359 focused host tests pass**, including14newfamilies and345retained cases. Current Activity/helper API compilation, exact two manifest-attribute audits, source wiring and outer-class bytecode isolation checks pass. All current compiled source hashes match. Evidence is kept in `.context/noble-fast-photo-close/verification.json` with complete logs and source/dependency hashes. Fourteen new host families cover seven state-policy/snapshot cases, three fake-dispatcher registration/cleanup cases and four constructor-free real Activity admission/snapshot/approval cases, alongside345retained photo cases. They do not construct/show/click actual dialogs, perform a real finish, swipe a gesture or execute Android lifecycle/rotation callbacks.

The current Activity and helpers compile against pinned API34/ML Kit APIs and exact unchanged current photo-model/native-permission API caches. Both manifests are parsed and audited for exactly one new attribute on their existing photo Activity declaration; no manifest merge/AAPT or SDK37 app compilation runs. The outer callback class is inspected for API33 window-symbol isolation. Retained access-store tests still explicitly control the native AtomicFile commit boundary with a JDK adapter; this is not Android durability/Keystore evidence.

Native controls/accessibility/layout, foreground transitions, providers/storage/Keystore, gestures/finish and physical devices remain untested. No full app/Gradle/native/APK build or packaged assets refresh. The final detached verification runner exited0; exact test totals and hashes are recorded in the verification manifest.
