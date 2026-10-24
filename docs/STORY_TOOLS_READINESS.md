# Offline video story tools

October 24, 2026. Two new user-facing workflows; zero new AI engines. Work performed solo, without computer-use tools. The existing app, designs, search foundation and credit/account policy remain intact.

## Built workflows

**Build a montage:** Project tools and the video inspector offer 15, 30 or 60 seconds. A deterministic local recipe chooses balanced centre excerpts in the existing order, redistributes time from short clips and samples up to 60 excerpts from large timelines. It never repeats, stretches or generates footage. Review the proposed actual length before applying. Short source footage can produce a shorter result; source-millisecond rounding can leave a small shortfall. This fast recipe is separate from the existing experimental sampled-frame AI rough cut.

The operation preserves per-clip speed, volume, grade, layout, title/style, edge/audio fade and intersecting source-relative captions, plus the current soundtrack. It replaces the timeline in one undoable edit using the existing project validation, preview, export and draft formats. It does not introduce a parallel renderer or project schema.

**Script to captions:** Titles tools and the video inspector accept one line per cue, up to 100 lines, 240 characters per line and 16000 input characters. Longer lines receive proportionally more time. Each cue has at least half a playback second. Timestamps cover the selected clip's source range and follow its existing speed through the caption pipeline. Review timing before replacing the clip's current captions. Undo restores them; individual cues remain editable and can be exported as project SRT. This is estimated timing from user-provided text, not speech recognition or generated writing.

Both are available to offline guests and account editor sessions. Dialog application rechecks the editor busy state, project/clip snapshot, lifecycle and account ownership. Invalid input or project limits leave prior state unchanged. No network service, model download, media upload, paid dependency or credit charge is added.

## Validation

- 130 RN-native JVM tests pass fresh, including 10 new story-tool cases; 112 shared JVM tests pass fresh. No failures, errors or skipped cases.
- New tests cover mixed speeds, short footage, large timelines, caption intersection, exact source coverage, text bounds/control/Unicode rejection, caption resolution, soundtrack retention, undo/redo, draft roundtrip and 80 deterministic randomized timelines.
- 60 React Native tests in 11 suites, TypeScript and lint pass freshly. Lint has 0 errors and 36 existing warnings.
- Both Android instrumentation compilations pass; no instrumentation was executed on a device. No live native screenshot or runtime claim is made.
- Full ARM64 FAST_INTERNAL APK build passes. All 784 frozen app inputs match; 257 bundled drawing assets, models/vendor digests, private activities/provider, signature and ZIP alignment are verified. New story-tool implementation and UI markers are present in DEX.

Artifact: `Honorable-1.0.3-story-tools-arm64.apk`
SHA256: `3da1516b5f61ddc4e2aeb9b70122f2758e44ed021cf1da38c17694773a45b18f`
Bytes: 129019287

Full evidence is retained under task outputs/Honorable-story-tools-*. The APK uses the public debug certificate and is for internal testing. Hosted account API remains unset. The previous drawing APK remains a separate, unchanged historical artifact.

## Physical acceptance still required

No Android phone is available. On a phone, install the new candidate and use Edit without an account. Import several short videos; open Project then Build a montage. Check actual sequence length, order, picture, music and caption timing. Undo and redo, save/restore the project, then export and inspect the saved copy. Repeat with mixed-speed clips and a short timeline.

Open Titles then Script to captions, paste several lines and review timing. Check replacement/Undo, cue editing, speed changes, trim/split and exported SRT. Cancel both dialogs; try switching clips or rotating before applying. Verify original media remains unchanged. These checks are pending, not passed.

AI generation, speech transcription, advanced compositing/rigging/nodes/3D, native iOS editors, hosted accounts and public-store readiness remain unfinished. No paid deployment or protected benchmark work was performed.

## Product document

[What you can make with Honorable](releases/Honorable-Creative-Guide.docx) explains cumulative changes from the oldest Git snapshot 424cf33, the two new tools, practical photo/video/drawing examples and the release limits. The earlier product update is retained in archive/releases/.
