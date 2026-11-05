# C081 — Inspect source audio levels and jump to a region

**1 new experimental source workflow; 0 separate fixes/scaffolding.** This inspects and navigates audio energy without editing the timeline. Graph, markers and time controls belong to this one workflow, distinct from C079's quiet-pause cut operation. No APK/device acceptance is claimed.

Video editor → Audio → Inspect source audio levels, also available in the inspector, reads the selected clip's original audio locally. A black/white overview shows duration-weighted RMS energy on a −60 to 0 dBFS display scale. Grey regions have insufficient known coverage; they are not drawn as silence. The summary reports known source coverage and the RMS of known audio only.

Tap/drag the overview, select one of three largest-energy average regions, or enter a source timestamp. Review this time updates the selection and its region-level label. Jump to selected time uses the existing source-to-edited-timeline mapping and seek path. Close cancels. No clip, caption, soundtrack, source file or undo-history edit is made. This is original audio before volume, mute, music and effects, not the final project mix or a speech/beat/true-peak measurement.

## Implementation and boundaries

`VideoAudioLevels` validates contiguous finite source windows and computes up to 96 default overview bars (bounded to 320). Powers, rather than amplitudes, are weighted by exact source-duration overlap, including partial final windows and bin boundaries. Bars with less than 90% known source coverage are unknown. Overall known-audio RMS remains separate from that per-bar confidence rule. Largest-region ordering is deterministic, excludes unknown/zero-energy bars and breaks ties by source time. Source-coordinate mapping clamps graph touches to the selected clip's exclusive end; typed timestamps must be inside the range.

`VideoAudioLevelsView` draws real derived energy with neutral unknown gaps and a selection cursor. It is decorative for accessibility: the labelled time field, Review button, live region-level text, largest-region buttons and explicit Jump provide the controls without graph gestures. The dialog requests a hidden initial keyboard and resize behavior. Actual rendering, accessibility and IME behavior remain untested on Android.

The workflow reuses unchanged C079 decoder/PCM windows and its bounds: at most two source minutes, one readable audio track, supported PCM16/float formats/rates/channels, bounded buffers, timestamp checks and cancellation/processing-loop deadline. No production dependency, permission, derived media file, upload, query history or new storage is added. Window data is transient and reduced before presentation. See [C079 decoder contract](FEATURE_C079_QUIET_PAUSES.md) for its runtime limitations.

Both audio operations share job/dialog ownership. Busy/paused entry is refused; a second analysis dialog cannot open over the current one. Project/clip/source-map/selected-index/soundtrack/mode/namespace/foreground generation are captured and checked before presenting or jumping. Pause/destroy cancels the worker, invalidates its generation and dismisses the dialog. Returning to the same editor does not revive old results. A cancelled/stale/failed inspection cannot change the timeline.

## Verification

- **25 host tests pass:** 8 new overview families, all 15 unchanged C079 PCM/cut/project-history regressions, and 2 actual Activity early-admission cases now covering both commands.
- New families cover independent duration-weighted energy examples; unknown/90%-coverage boundaries; 320 bins across 6,000 windows against an independent overlap-energy oracle; source coordinates/exclusive end; known dB reference values and invalid inputs; deterministic region ordering; detached source snapshots; and malformed/allocation bounds.
- Constructor-free Activity instances with poisoned source/widgets verify that paused and busy calls return before Android/source access. This does not execute real lifecycle callbacks or widgets.
- Current Activity, graph view, model, decoder and shared helper sources compile against the isolated API34/Media3 surface. Cached core-model and source-to-timeline helper hashes match current source. Exact source/dependency hashes, compile/test logs and evidence: `.context/noble-fast-audio-levels/verification.json`.
- The already completed main checkpoint `8e1f5f8` was independently checked against all 47 committed file hashes before publication. Publishing it preserves its four previously counted source workflows and six prior fixes; it does not add them to this feature's count.

No real source-audio decoding/level accuracy, graph rendering/touch/IME/TalkBack, seeking/playback/export/AV-sync, performance, configured SDK37/Gradle/full native/APK build or device/store acceptance. Existing framework deprecation warnings remain. Source/API compilation and synthetic host tests establish none of those runtime results; the larger app build stays deferred to the owner's feature-batch checkpoint.
