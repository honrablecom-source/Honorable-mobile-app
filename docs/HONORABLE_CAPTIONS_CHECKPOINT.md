# Timed captions and subtitle interchange

October 21, 2026. Source: the existing authoritative Honorable repository, branch `codex/android-internal-readiness`, draft PR #1. Continue the existing app; UI redesign is paused.

## Delivered scope

**2 new user-facing features, built for internal Android testing; not device-accepted.**

1. **Editable timed captions.** Add at the playhead, edit text and original-source start/end, delete or clear a clip's cues. Non-overlapping cues follow trim/split/reorder/speed into the same Media3 composition used for rendered preview and burned-in video export. Undo/redo and bounded local/encrypted recovery retain captions. Caption text wraps fully with caption-only height fitting; existing title behavior remains.
2. **SRT interchange.** Strict bounded UTF-8/BOM/CRLF parsing, timestamp/range checks and atomic clip replacement. Imported times refer to the original selected source before trim/speed; the confirmation explains this. Exported SRT uses the edited project's output timestamps and applies the selected pending trim. Copies check separation from every protected project original before truncation and reread/verify bytes.

Use Studio's existing Video picker, then **Titles → Captions**, or **Tools/inspector → Timed captions**. Cue times are source seconds, to millisecond precision. Limit: 200 cues per clip, 1,000 per project, 240 UTF-16 text units / three explicit lines per cue, 64,000 caption text bytes per project, and 128,000-byte SRT files. A combined recovery budget can reject a large edit before changing existing state.

This is manual caption editing and subtitle interchange. **No automatic speech transcription, new AI model or generative engine was added.** No media upload, paid service or model download.

## Review and correctness

Three agents worked on separate assignments: read-only caption review, Obsidian coordination and exclusive Android validation/build. The director integrated source and findings. The reviewer identified repeated long subtitle references exceeding the codec budget. The unshipped v6 format now deduplicates the reference table and uses per-clip indices; exact payload budgeting reserves maximum source/soundtrack/header space before accepting edits. A regression round-trips 120 repeated 2,048-byte references and confirms oversized edits preserve the current project/history. Independent review checked the byte calculation and versions 1–5 compatibility. Fixes and project notes count separately from the two features.

Rendering owns only the active caption bitmap; transparent gaps and backward seeking are explicit. Source URIs remain protected through bounded edit history. Imports reject unreadable/unprotectable originals. Saved subtitle copy failures report partial-copy cleanup when applicable. Real provider, cancellation and lifecycle behavior remains a phone acceptance task.

## Verified evidence

| Check | Result |
| --- | --- |
| RN-native JVM | 82 fresh cases, zero failures/errors/skips; 13 new caption cases |
| Shared Android JVM | 98 previously passing cases reused after current/base/prior-tested shared Git inputs match; not rerun |
| React Native | 32 tests / 9 suites pass; TypeScript passes |
| Lint | 0 errors / 36 existing warnings; generated Android build output excluded |
| Caption instrumentation | Five cases compiled only: composition effect order, gaps/cache/backward seek, full-length text fitting, Unicode SRT copy and hard-link refusal |
| Complete Android build | ARM64 FAST_INTERNAL; one Gradle worker, in-process Kotlin, 1 GB heap / 512 MB metaspace; other owner apps kept open |
| Frozen source | 485 tracked/nonignored untracked app input hashes and membership match before/after build |
| APK inspection | Package/ABI/manifest, existing/new code markers, bundled models/vendor digest, expected certificate, ZIP alignment and delivered-copy hash pass |

Artifact: `Honorable-1.0.3-captions-arm64.apk`. Bytes: 112049309. SHA-256: `d54c45b151a7598d2ad5e63c6dd5105098e95058ebb7d92e9b86163fcc0bc971`.

Evidence directory on this workstation: `C:/Users/Shadow/Documents/Codex/2026-10-18/you-are-now-the-acting-director/outputs`. Records: `Honorable-captions-apk-verification.json`, `Honorable-captions-build-provenance.json`, `Honorable-captions-frozen-inputs.json`, `Honorable-captions-native-test-summary.json`, `captions-native-test-results/`, RN/Jest/lint logs and full build log `Honorable-captions-build.log`. The artifact verifier records the committed source identity after publication. The earlier failed test-expectation log is preserved; final evidence uses corrected source.

## Obsidian in use

The installed Obsidian app opens this repository's `docs` vault. [[START_HERE]], [[SESSION_HANDOFF]], [[WORK_QUEUE]], [[DECISIONS]], [[TEAM_WORK]] and [[CAPTIONS_WORKLOG]] are the working project memory. The live work log was read/updated during implementation and review, and workspace state confirmed that note open. Core plugins only; community plugins and sync are disabled. Machine-local workspace state is ignored. Vault setup adds **0 app features** and has no measured total-token savings claim.

## Remaining gates and next task

Public-debug-signed, unshrunk internal test APK. No phone is available, so caption pixels/readability, rendered preview/export agreement, mixed codec/audio/HDR behavior, provider failures, aliases, cancel/picker rotation and process recovery are not device-verified. Owner visual acceptance, hosted account API/sign-in, private signing/store, full photo/video generation and iOS remain open. No investor-ready claim.

Next bounded engineering task: evaluate actual local speech transcription runtime/model, license, hardware and transcript timing using isolated sample media before any integration claim or large download. Then connect editable transcript output to this caption infrastructure. Continue project persistence/masks/layers/compositing using the existing architecture. Billing/hosting remain deferred and protected Seran holdout stays untouched.
