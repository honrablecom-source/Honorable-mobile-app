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
