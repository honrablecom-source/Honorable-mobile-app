# Android internal candidate device checks

Record artifact SHA-256, source commit, app version/build, Android API, ABI and coarse memory class. Use synthetic photos/videos containing no personal information. Record pass/fail plus observed behavior; an unchecked row is unverified.

## Prerequisites

The internal APK uses the public debug key and package com.honorablemobile. It is not a store artifact. A different signing key cannot update an existing installation of the same package; preserve local data before deliberately uninstalling anything.

Configure the public Google Web OAuth client ID and HTTPS account API URL at build time, register the APK signing certificate with the Android OAuth configuration, and provide an authorized beta account. No client secret belongs in the app. Account session and Studio rollout authorization must work before editor testing.

## Core flows

| Check | Expected observation |
| --- | --- |
| Install and cold launch with Metro stopped | UI loads from bundled JavaScript. |
| Google sign-in, cancel, relaunch and sign-out | Valid session restores; cancellation creates no session; sign-out clears it. |
| Denied, selected-media and full-library permissions | Appropriate explanation and only authorized media appears. |
| Index, restart and refresh after media changes | Durable progress and local library update without duplicate entries. |
| FAST/V1 and VIDEO/V2 searches | Real local matches; trusted completion required before final billing. V3 stays locked. |
| Failed/cancelled search | No charge. Successful FAST/V1 costs 1, VIDEO/V2 costs 3; free credits first. |
| Open photo editor | Original orientation is correct; source remains untouched. |
| Crop, rotate, adjustments, undo/redo, compare/reset | Preview matches chosen state and export matches that state. |
| Save copy, cancel picker and invalid destination | Cancellation is harmless; errors are recoverable; repeated saves do not overlap. |
| Large square, panorama and low-RAM export | Output reports actual dimensions; smaller decode fits the budget; no crash. |
| Leave editor during preview or export | No crash or stale screen update; no source mutation. |

## Read text (new on-device AI tool)

1. Use a sharp synthetic Latin-script receipt/sign image. Open the editor, select Read text, review recognition and copy only after pressing Copy text. Confirm line order and visible text; recognition can be imperfect.
2. Use a photo without writing: show a no-text explanation and allow retry.
3. Rotate/crop the image and read again: recognition should use the current edited preview, regardless of Compare mode.
4. Open the authorized editor, enable airplane mode and read text: bundled recognition should still work. Reopening the editor or exporting may require the existing account authorization service.
5. Leave while recognition is running; repeat after returning. No crash, duplicate processing, leaked dialog or private text in logs/telemetry.
6. Try a low-memory device and unsupported/corrupt image. Show a recoverable message.

No device results have been recorded in this session. Do not treat JVM tests or APK packaging as evidence for these flows.

## October 18 Video Editor V1 acceptance — blocked, not run

The owner confirmed no Android phone is available. `adb devices -l` returned no devices. The three pure Kotlin project tests run on the JVM; trim/composition instrumentation tests compile but require an actual device to execute. Do not label compilation as a media-output pass.

When an authorized ARM64 Android phone and configured account service are available, use the exact new internal APK hash recorded in the current status/provenance. Preserve existing app data and signing compatibility; do not bypass editor/account authorization.

1. Cold launch with Metro stopped; test genuine sign-in/session restore and media permissions.
2. Home New Workspace Photo and Video, plus Studio Photo/Video: choose a specific item from the system picker; verify cancellation opens nothing and no automatic first-library-item selection occurs.
3. Play, pause and seek; separately test trim, split, delete, reorder, undo, redo and reset.
4. Test 0.5x/1x/1.5x/2x speed and 100%/50%/mute; inspect actual rendered duration, clip order, visible frames and audio.
5. Save a new copy through the system document picker; reopen it and check H.264/AAC, portrait/landscape orientation, playback/audio sync and intended provider/Gallery discovery. Automatic MediaStore insertion is not implemented by this path.
6. Record original-file hashes before/after; test aliased-destination rejection, cancellation while rendering/copying, invalid output and failed-save cleanup/partial-file reporting.
7. Run the targeted native trim/composition tests on the compatible test target, then return to the exact internal artifact for UI acceptance. Record actual device/build/results and never infer a pass.
8. Demonstrate a grounded development-media search → video timestamp → edit → save-copy flow with existing trusted completion/ledger intact. No protected holdout, V3 unlocking, auth bypass or unrelated Studio expansion.

All checks above remain NOT RUN until recorded on a physical device. Later roadmap work stays queued behind this core acceptance gate.

## Local core-reliability changes — additional device checks, NOT RUN

- From a known video search result, record the displayed found timestamp. Select Edit and confirm the preview opens at that source moment within the player's seek behavior. The initial project must still span the entire original video; opening a found moment must not silently trim the source.
- Repeat for a result at zero and near the end, plus ordinary library and New Workspace videos. Confirm those entry points use the same selected source and existing Studio authorization.
- Seek within the editor, rotate/recreate the screen, and compare the restored preview position. Separately verify split/trim/undo/redo/export remains correct; source hashes must remain unchanged.
- Record cold and repeated local search timing on development media. If a transient model failure occurs, record the error and retry result. The JVM fixtures establish cache recovery and bounded successful caching, rather than actual phone latency or real-model quality.

Billing/live hosting is deferred at the owner's request. These device checks still require the existing authorized account service; no bypass is part of the internal artifact.

## Local video drafts — physical acceptance NOT RUN

1. Open a chosen video through the existing authorized flow. Split/reorder clips, change speed/volume/mute, choose a pending trim and preview position, then press Save draft. Confirm the app reports success only after storage finishes.
2. Close/relaunch the app, select the same video, and press Restore draft. Compare clip order/ranges, audio settings, pending trim and preview position. Export and inspect the actual resulting media separately; source hashes must remain unchanged.
3. Make different clip edits before restoring. Restore the draft, then Undo/Redo; verify prior clip edits are recoverable. Undo history from the previous app launch is not persisted.
4. Open another video; the first video's draft must not appear. Switch accounts and sign out/expire the session; the former account's draft must not be restored under another identity. Returning to the original authorized account may restore its own draft.
5. Delete, replace or revoke access to the original source. A draft is a local editing recipe, not a media backup; inaccessible or duration-incompatible sources must not report a successful restore/export.
6. Leave the screen while saving/restoring, repeat actions, and simulate storage failure on a test device. No overlapping project changes, crash, wrong-account restore or false saved message. Corrupt/future/oversized draft payload rejection is covered by JVM tests; Android keystore/AtomicFile behavior still needs device execution.

Drafts use the existing encrypted no-backup storage and one explicit saved draft per account/API/source. Saving does not overwrite the media, automatically reopen edits, or create a completed export. These controls do not establish multi-source projects, multitrack editing or generative AI.

## Person cutout and verified photo copy — NOT RUN on a device

1. Use consented or synthetic photos with one person, multiple people, fine hair, dark/light backgrounds and no person. Tap Cut out person (beta); check that unclear detections fail visibly instead of pretending a successful cutout. This is person segmentation, not arbitrary-object removal or generative fill.
2. Inspect soft edges against the transparency grid, then crop/rotate/adjust, Undo/Redo, Compare and Reset. Preview and saved transparent PNG must use the same source-aligned matte. Check skin, hair, clothing, partial occlusion and small subjects; record failures honestly.
3. Save a PNG copy, reopen it in another alpha-aware viewer and inspect actual pixels/dimensions/transparency. Hash the original before/after. Verify a differently named URI that aliases the original is rejected before truncation; use synthetic files/providers for destructive-path testing.
4. Test local Documents/MediaStore providers, unknown/non-seekable identities, unavailable read-back, disk-full and failed writes. A saved message requires read-back hash and PNG dimension/type verification. Failed copies may leave a partial destination; no automatic deletion of user documents occurs.
5. Change edits while inference runs, close the editor mid-task, repeat inference and export, and test low memory. Stale inference must not overwrite the newer edit; owned inference inputs and mattes must not be recycled while in use. Photo edit persistence across recreation remains a separate unfinished lifecycle requirement.
6. After the authorized editor opens, enable airplane mode and use person cutout. The model is bundled. Record actual device behavior and latency; bundled assets and JVM tests are not offline/runtime proof.

The new pixel/transform tests are Android instrumentation tests. Their compilation is not execution or image-quality evidence. The bundled ML Kit selfie-segmentation SDK is beta; model/hardware quality and modern-device compatibility require acceptance before release.

## Functional video batch — not run on a device

- Split a source into clips, select a later clip, narrow its pending trim and run scene suggestion. Sampling targets and suggested range must remain in that effective selection.
- Change trim, selected clip, order, speed or volume during analysis. A late result must preserve current edits and ask to rerun.
- Exercise inference timeout, background/foreground and activity destruction. No crash, stale result or recycled-bitmap access.
- Apply trim, then Undo/Redo. Preview, saved draft and exported clip must retain the intended source range.
- Duplicate a trimmed/speed-adjusted clip with custom volume. Verify adjacent duplicate, independent subsequent edits, Undo/Redo, saved draft and composition/export duration/order/audio.
- Mute a clip, export and inspect audio. Undo must restore its previous volume.
- Select each real clip-strip item; verify the matching source range, labels and preview seek, including after delete/reorder/reset/restore.
- Verify system navigation contrast on Android 24/25 and Android 26+, plus modern edge-to-edge behavior. UI remains white/outlined; original media colors unchanged.
- Compare original file hashes before/after all exports. Test provider denial, cancel, alias destination and storage failures.

JVM tests and successful APK compilation do not execute these media/device cases. No device acceptance is recorded for this batch.
