# Timed captions — work log

Status: **implemented and built for internal Android testing; physical acceptance pending**. October 21, 2026.

## Intended outcome

Editable timed video captions, SRT import/export, captions in the same composition used for preview and export, and compatible local recovery. Automatic speech transcription is outside this increment.

## Confirmed existing architecture

- `VideoEditProject.kt`: immutable clip data, bounded undo/redo and source duration validation.
- `VideoCompositionExporter.kt`: common Media3 Composition for preview/export; composition-wide effects already support time-aware rendering.
- `VideoDraft.kt`: bounded 256,000-byte versioned binary codec; older drafts must remain readable.
- `HonorableVideoEditorActivity.kt`: existing Titles/Audio inspector and system document-picker workflows.

## Work entries

- Read the source and feature register; captions/transcripts are currently planned.
- Selected caption infrastructure as the next functional milestone. No model/service is installed or claimed.
- Established this work log and linked queue before implementation.

## Acceptance evidence

Pending: parser/timing/history/recovery regressions, native compilation, integration-test compilation and full internal APK verification. Real-device rendering remains an external gate.

## Timing and persistence decision

Caption cue times attach to each clip's original source. Trim/split/reorder/speed transformations resolve these cues into final composition time. Imported SRT uses source timestamps for the selected clip; exported SRT uses the edited project's output timestamps. The import confirmation explains this distinction. Caption text and imported document references remain in bounded undo/history and compatible v6 encrypted/local recovery. Existing draft versions stay readable.

Implemented the pure caption/SRT timing model, bounded UTF-8 parser, shared composition overlay and protected sidecar writer. Integrated clip controls, draft v6 and import/export callbacks. These changes are **unverified drafts until tests/build checks pass**.

## First validation review

The first native run compiled the source and completed 81 JVM tests: 80 passed and one timing expectation failed. The failing expectation had overlooked intentional coalescing of identical adjacent caption text; the test now uses distinct text to independently verify reordered output timing. Review also tightened bounded document reads and cancellation, original-file descriptor validation, trim/split cue retention, atomic project limits and SRT export's pending trim handling. Native/integration checks will be repeated on the final source.

## Validation progress

The corrected native run passed **81 JVM cases**, including **12 new caption regressions**, and compiled Android integration tests. Additional instrumentation coverage now checks Unicode SRT output and hard-link refusal. Renderer review found that ordinary title ellipsis could silently shorten long captions; caption-only fitting now wraps the complete text within a bounded height while retaining the existing title behavior. Final source checks and packaging remain pending.

## Multi-agent review and final freeze

The owner explicitly requested multiple agents. A read-only caption reviewer found that repeated long imported subtitle URIs could exceed the recovery codec limit. The new, unshipped v6 format now deduplicates these references; exact caption payload budgeting reserves the maximum source/soundtrack/header size and rejects an oversized edit before changing history. A regression covers 120 repeated 2,048-byte references and atomic oversized-edit refusal. The reviewer independently checked the byte calculation and compatibility with versions 1-5, with no further actionable finding.

The documentation agent created TEAM_WORK and linked it from START_HERE; 28 wiki links across 31 notes resolved without ambiguity or missing targets. The build agent now owns the only Gradle process. Final expected validation: 82 fresh native JVM cases (13 new caption cases) and five caption instrumentation cases compiled only. React Native: 32 tests pass, TypeScript passes; lint excludes generated Android output and reports 0 errors / 36 existing warnings. APK production and full verification are pending.


## Completed engineering milestone

Final frozen source passed 82 fresh native JVM cases (13 new caption regressions); 98 shared cases reused after unchanged Git inputs. All five new caption instrumentation cases compiled, without execution. 32 RN tests / nine suites and TypeScript passed; final lint 0 errors / 36 existing warnings. Complete ARM64 internal build and independent APK package/code/model/signature/alignment/copy verification passed with 485 unchanged frozen app inputs. Artifact `Honorable-1.0.3-captions-arm64.apk`; SHA-256 `d54c45b151a7598d2ad5e63c6dd5105098e95058ebb7d92e9b86163fcc0bc971`. Source/remote publication is verified separately by the milestone's GitHub review record.

**2 new user-facing features; 0 new AI inference/generative engines.** Obsidian and multi-agent coordination are scaffolding, and the recovery budget correction is a fix. Physical rendered caption/media/provider/lifecycle acceptance remains open. Next: real local speech transcription feasibility, then editable transcript integration. See [[HONORABLE_CAPTIONS_CHECKPOINT]] for full evidence and limits.
