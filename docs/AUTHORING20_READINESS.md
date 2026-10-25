# Authoring20 — October 25, 2026

Owner request: add twenty further photo/video creation features as one batch, then compile/build together. Work solo. All twenty have source implementations and editor entry points; **full Android compilation/APK validation is pending**. They are not yet device-tested. Do not confuse this batch with the preceding Creative20 effects batch.

## Ten photo composition tools

1. Canvas padding with transparent or chosen solid background; no generated fill.
2. Photo frames with user-chosen color and width.
3. Alpha-aware soft drop shadows.
4. Fading photo reflections.
5. Shape cutouts (shape choices are variants of this one feature).
6. Repeated photo tile patterns.
7. Radial kaleidoscope composition.
8. Top/bottom perspective poster transforms; not lens correction.
9. Repeated text watermarks with opacity and color choice.
10. Feathered color-key transparency; not subject recognition or secure redaction.

`PhotoComposition` holds validated scalar/text state. The shared raster renderer applies composition to its private edited bitmap and uses the same path for preview and output. Presentation keeps the decoded image's pixel budget; the photo can scale down to fit new canvas area/reflections. New controls are in **Create & compose** and the photo inspector. Existing adjustment looks deliberately retain the target photo's composition instead of transferring framing/watermarks. Version 7 lifecycle recovery reads all older versions and retains current/pending edits plus nearest history within 64 KiB. Active editing history bounds are unchanged.

## Ten video authoring tools

1. Ripple-delete a selected source interval.
2. Keep captioned ranges with optional handles; no speech detection.
3. Manual tempo/beat-grid cuts across the project, accounting for clip speeds.
4. Four-step accelerating/decelerating speed ramps using supported playback speeds.
5. Interleave source videos while retaining each source's clip order.
6. Word-group captions with estimated timing inside existing cues; no speech alignment.
7. Merge nearby caption phrases with duration/text bounds.
8. Burn original-source timecodes into the selected clip using the caption renderer.
9. Caption-led ducking of selected-clip audio; soundtrack stays unchanged. Disable that clip's fades first.
10. Storyboard titles from one pasted line per clip.

`VideoAuthoringTools` creates bounded pure proposals. All are exposed through existing batch editing and inspector lists, reviewed against the current project/account snapshot and applied as one undoable edit. The existing project/draft format, source-relative captions and shared Media3 preview/export handle the result. No new project format or media upload path was introduced. Existing source, credit and ledger authority remain intact.

## Evidence so far

- 116 focused JVM cases pass, 0 failures/errors/skips, including 39 new photo/video regressions. Existing photo effects/creative color, preset, recovery, video project/draft and batch/story tests are included.
- Initial run: 115 cases, one tiny 9px rounded-corner fixture assertion failed because its pixel center was inside the curve. Fixture now uses a meaningful 32px raster; algorithm was not weakened to fit it.
- Android 37 API compilation of the new photo controls, composition renderer and shared raster pipeline passes locally. This is not an app build or device execution.
- Added three Android photo renderer/copy/alpha cases and two shared video composition cases. They are not yet compiled or executed.
- Local combined check uses real source files, Kotlin 2.2.0 and JDK 17 in `/tmp/noble-authoring-jvm/`; full logs and JUnit XML retained there. It is a focused JVM verification harness, not an Android APK build.
- Complete Android build, RN validation and APK matrix for this batch remain pending. No per-feature APK builds were run.

The earlier Noble packaging pipeline `40235549888` passed completely for source `6659e1e`, before Authoring20. Normal APK checks and strict eight-APK matrix verification/upload all passed. Its output must not be described as containing these twenty additions. Two earlier normal APKs are staged locally in `executable/`; final delivery must replace/update every APK type and publish through LFS with exact source/hash records.

New user-facing features: **20 source implementations pending Android build**, zero new AI engines. Scaffolding/tests/guards are not extra features. Android phone acceptance, native iOS editor parity, signed phone IPA, production sign-in/payment operation and store readiness remain open. miniPaint is not integrated; this batch reuses the existing Android/Media3 engines without adding dependencies.
