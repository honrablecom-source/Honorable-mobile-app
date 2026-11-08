# C103 — Assemble pasted video source ranges

**1 newly added source workflow; 0 separate fixes; 0 scaffolding.** Shared register: 100/500, 62 beyond the 38-outcome batch baseline. Implemented in native source and checked with focused host/API34 tests; no full APK build or real-device acceptance.

Open Batch editing → Assemble pasted source ranges. Paste 1–100 lines, each containing two original-source timestamps in decimal seconds separated by a comma:

```text
5.000, 7.500
1.250, 2.750
5.000, 7.500
```

The selected clip becomes three independently editable excerpts in exactly that order. Repeated and overlapping ranges are intentional; the tool never sorts or merges them. Every range must remain within the selected clip's current original-source bounds and last at least 0.1 playback second at its current speed. This is a simple shot-list format, not CMX EDL, frame timecode, automatic scene analysis or speech detection. Decimal points and at most three fractional digits follow the existing bounded seconds parser. Blank rows and CRLF are allowed. Invalid input stays editable, without changing the project.

Review displays every proposed source range, the project duration and replacement behavior. Apply is one existing project transaction and one Undo. Other clips and soundtrack settings stay. Each excerpt keeps the selected source, title, styling, color, framing, fades, original audio and source-anchored motion. Captions intersecting the excerpt retain their original timestamps and subtitle-document provenance. Edge/fade settings repeat on each new excerpt. Shortening/reordering footage changes its relationship to the unchanged soundtrack; preview before export. Codec cuts are not promised frame-exact.

The tool uses the existing tracked text editor and immutable batch proposal. Exact visible dialog/request ownership plus the full project/source/duration/range/private-session/generation snapshot guards application. Late/hidden/foreign/paused callbacks cannot apply; successful or failed approval cannot revive after Undo. Text clears on dismissal. A whole-clip-only proposal is a no-op. The 1000-clip, caption-count, caption-text, title-text and duration bounds are checked before acceptance. Original media is never written or uploaded.

The novelty audit compared existing caption-led cuts, saved-note highlights, manual interval removal, center-excerpt montage and beat-grid cuts: none accepts a pasted list of arbitrary selected-source ranges and preserves their entered order/repetition. This is one workflow, not separate counts for row syntax, presets or repeated pieces.

## Evidence and limits

511 focused host tests pass: 16 new and 495 retained. New tests cover entered order, overlap/repeat, strict syntax, CRLF/blank rows, invalid/outside/reversed ranges, speed-aware minimums, text/row bounds, unaffected clips and appearance, caption intersections/provenance, clip/cue/text growth limits, no-op, actual Activity approval/one Undo, stale/hidden/foreign refusal, codec recovery and paused entry before widgets. Current Activity, range helper, extended enum and tests compile against pinned API34 references and the hash-verified retained graph. Existing enum entries remain in the same order. 21 source checks verify menu reachability, tracked editor/review/cleanup, exact review ranges, selected position, guards and unchanged project/codec/caption/review implementations.

The source checker initially referenced a nonexistent `VideoDraftStore.kt`; corrected to the actual `VideoDraft.kt`, then all 21 checks passed. Production code/tests were unchanged by that checker correction. Evidence: `.context/noble-fast-range-assembly/verification.json`, `tests.log`, `compile.log`, `wiring.json`, `exit.json`.

Host Activity fixtures use constructor-free metadata objects and execute actual approval/project functions. They do not instantiate the Android input/review widgets or execute dialog automatic dismissal, accessibility, physical lifecycle, provider access, preview/rendered/exported pixels or device behavior. No configured SDK37/full module/Gradle/native/APK build or production readiness is claimed. No dependencies, personal media uploads, search/ranking, credit/ledger or benchmark changes. Broad build remains around 138 source workflows.
