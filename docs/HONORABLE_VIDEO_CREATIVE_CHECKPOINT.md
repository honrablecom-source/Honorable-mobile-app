# Video creative editing checkpoint — October 19, 2026

## Implemented and build-verified

- Clip titles: plain text, up to 120 UTF-16 characters and three lines, with bounded wrapped lower-third rendering. The bitmap is generated locally and released with the overlay. Very long text may be ellipsized. Titles remain through trim/split/duplicate/reorder and can be removed with a blank title.
- Visual fades: optional per-clip fade-in/out through black, up to 0.5 seconds per edge, shortened for short clips. Applied at final output time after speed changes, including titles. Audio is unaffected; these are not overlapping crossfades.
- Project soundtrack: document-picked audio with durable read permission, default 25% gain, volume cycling and removal, mixed with existing clip audio. The separate audio sequence loops and is bounded by the non-looping video sequence. Up to 1,000 repeats are allowed to bound preview resource use. Clip-only preview intentionally isolates clip audio; use Preview project for the soundtrack mix. No ducking, soundtrack trimming, audio keyframes or general multitrack UI yet.
- Unified undo/redo includes soundtrack and clip edits; Reset removes both. Export protects video originals, current soundtrack and soundtrack documents still referenced by history before truncation.
- Draft v3 stores titles, fades and soundtrack metadata; v1/v2 remain readable. v3 uses strict, length-bounded UTF-8. Total title UTF-8 text is bounded at 128 KB; encoded recovery state is capped at 256 KB. Imported documents must remain readable with their recorded durations. No media bytes or credentials are stored in the draft.
- Corrected Media3 duration metadata: full original source duration is supplied for preview/export, with clipping handled separately. Previously, trimmed length was passed as source duration and could invalidate nonzero-start previews. Clip preview now uses the effects engine. Split positions map output playback time back to the appropriate clip, source and speed.

## Evidence and limits

The initial title/fade implementation passed 30 native JVM tests, instrumentation compilation and a full internal build. The final expanded batch passed 37 native JVM tests (8 new creative/timing/Unicode cases and 6 new soundtrack cases), instrumentation compilation and a full combined internal build in 2m08s. Independent APK checks passed for package, non-debuggable ARM64 configuration, bundled JS/native feature markers/models, approved signing certificate and ZIP alignment. Shared photo/Compose code is unchanged from its prior 70 passing JVM tests. 67 Node checks passed in this batch; Node/JS/build configuration is unchanged by the subsequent soundtrack work.

Android instrumentation includes source-duration/effect-chain checks, bounded title bitmap allocation/release, soundtrack sequence routing and audio-original alias protection. It has not run: no Android phone is available. GPU pixels, typography at different aspect ratios, fade timing, mix loudness/clipping, loop boundaries, AV synchronization, provider permissions and activity recreation remain device validation work. A successful build is not proof of perceptual or device quality. Public-debug-key, ARM64 internal artifacts are not store releases. Native hosted account service, UI acceptance and iOS remain incomplete; billing remains deferred.

## Next

Overlapping crossfades and richer title timing/styles, soundtrack trimming/fades, track UI, then video grading/keyframes. Advanced photo masks/layers/curves, actual image/video generation, rigging/animation, professional node/3D/code Studio and iOS still require engineering. No investor-ready claim.

## Engine references

The pinned Media3 API distinguishes original duration from clipping: https://github.com/androidx/media/blob/1.11.1/libraries/transformer/src/main/java/androidx/media3/transformer/EditedMediaItem.java. Composition and looping sequence APIs: https://developer.android.com/media/media3/transformer/composition and https://github.com/androidx/media/blob/1.11.1/libraries/transformer/src/main/java/androidx/media3/transformer/EditedMediaItemSequence.java. Overlay/RGB effects use the existing Media3 effect dependency; no new paid service or library was added.

## Delivered internal APK

Honorable-1.0.3-video-creative-arm64.apk

SHA-256: `eabf3b7ebc6bfb334303e66c8c62e9071fa15b6b45e7f6ca2a28d7a1db29e519`

122 frozen app input fingerprints matched. The delivered artifact uses opt-in unshrunk internal mode and the public debug certificate. It is not a store release or device acceptance result.
