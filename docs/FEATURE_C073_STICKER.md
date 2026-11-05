# C073 — Rounded-outline sticker copies

**1 new experimental source workflow; 0 separate fixes or scaffolding.** Photo editor → Transform → Make outlined sticker creates a separate local PNG workspace from transparent edited artwork or a person cutout. Choose a white or black border from 2–32 preview pixels. The generator trims empty outer space, adds padding and draws a rounded border around the visible pixels. Existing source colors and alpha stay unchanged in the pixel kernel, including faint edges. Opaque images receive guidance to use transparency or cutout first.

The result is a flattened image at editor preview resolution. It does not retain upstream layers or editable border settings in the new workspace. The previous workspace keeps its editable project, Undo history and protected originals. Save an image project or export PNG to retain the new asset; its starting cache file is temporary. The confirmation explains these limits before creating the image.

## Open-source reuse

Noble adapts the squared Euclidean distance-transform routines from [Mapbox TinySDF v2.0.7](https://github.com/mapbox/tiny-sdf/tree/v2.0.7), under BSD-2-Clause. The Kotlin port uses a bounded Float grid with Double scratch calculations. Noble does not include TinySDF's browser canvas/font runtime or add a JavaScript graphics dependency. The foreground-pixel preservation, padding, border coverage and photo workflow are Noble's integration.

The full copyright and license are retained in the Kotlin source and `android-app/app/src/main/assets/TinySDF-LICENSE.txt`. The React Native Android app already includes that shared asset root. One physical asset serves both builds, avoiding duplicate asset entries. `THIRD_PARTY_NOTICES.md` links the pinned source and notice. Actual APK asset packaging has not been run.

## Checks and limits

The current photo/shared API compile passes and **299 host tests pass**, including 12 new sticker cases. Tests cover padding, faint-alpha/source preservation, hidden RGB removal, rounded geometry, border color, invalid allocation/radius, empty/opaque refusal, output bounds, cancellation, independent brute-force comparisons over 60 synthetic masks, and actual paused-Activity admission before UI access. Existing C071 trim checks remain in the passing suite after its transaction code was shared with sticker creation.

The native adapter, dialog, source rendering, Android PNG codec, foreground callbacks, provider/navigation behavior, persisted image projects and original-file integrity have not been executed on a device. No full app/Gradle/native/APK build or bundled drawing-asset refresh occurred. A host PNG gallery was generated from actual kernel outputs, visually inspected and round-tripped with unchanged ARGB using Java ImageIO. It is a synthetic host check and does not replace Android bitmap/PNG acceptance. Evidence: `.context/photo-sticker/verification.json`.

The new controls use Noble's white/black native styling. Transparent source images now receive the existing checkerboard background so a white outline can be inspected. Each new transform action has its own row. Actual touch, large-font, TalkBack and lifecycle behavior remain pending.

Generation captures the photo, edit, original-source chain, session and foreground generation before confirmation. It freezes selected border settings at confirmation. Stale or background work cannot publish a new workspace; temporary files are cleaned on failure or stale completion. No source file writes, personal-media upload, search/ranking or credit changes are introduced.

The shared register now contains 72 source workflows, with 34 added beyond baseline 38. C072 and its evidence are preserved. No source feature count is promoted to full-app or device readiness.
