# Noble Authoring20

Twenty additional local photo/video tools in one source batch. Full Android build and device acceptance are pending at this checkpoint. No new AI engine, generation model or speech recognizer is claimed.

Open a photo, then **Create & compose** (also in the inspector):

| Tool | What you can create |
| --- | --- |
| Canvas padding | Transparent or solid space around a photo, within its safe export pixel budget. |
| Photo frame | A colored frame without filling transparent interior areas. |
| Drop shadow | A soft shadow following the photo's alpha. |
| Photo reflection | A reflected copy fading below the photo. |
| Shape cutout | Circle, rounded, diamond or heart masks; one tool with shape choices. |
| Repeat pattern | A tiled pattern from the selected photo. |
| Kaleidoscope | Radially repeated and mirrored photo detail. |
| Perspective poster | A top- or bottom-pinched projective image. |
| Repeated watermark | Repeated local text with opacity and black/white choice. |
| Color-key transparency | Feathered transparency around a chosen color; use PNG to retain alpha. |

Apply, compare the preview, then export a separate copy. Undo restores the preceding edit; Reset tool removes only that tool's contribution. Adjustment looks retain the target photo's composition instead of transferring another photo's watermark. Padding does not generate missing scenery, perspective does not repair lens distortion, and color keying is not secure redaction.

Open a video, then **Project → Batch editing** (also in the inspector):

| Tool | Workflow |
| --- | --- |
| Ripple-delete interval | Start/end source-time offsets on separate lines remove a middle interval. |
| Keep captioned ranges | Retain caption ranges with optional handles. |
| Manual beat-grid cuts | Choose BPM and beats per cut for a project-wide tempo grid. |
| Stepped speed ramp | Four editable pieces accelerating or slowing through supported speeds. |
| Interleave source videos | Alternate sources, retaining each source's clip order. |
| Word-timed captions | Estimated word-group timings inside existing cues. |
| Merge caption phrases | Join nearby captions within gap, text and duration limits. |
| Burn in source timecodes | Replace selected-clip captions with original-source timecode labels. |
| Caption-led clip ducking | Lower original audio while captions are active; disable this clip's fades first. |
| Storyboard titles | Paste exactly one title line per clip. |

Every tool prepares a review. Apply and preview changes the existing project as one undo step; then use the existing export. Original files stay unchanged and project soundtrack is retained. Beat grids are manual, word timings are estimates, and ramps are stepped; these tools do not detect speech/beats or synthesize intermediate frames.

So far: 116 focused JVM cases pass (39 new), and Android 37 API compilation of photo controls/renderers passes. Five new instrumentation cases await app compilation and a device. See [the readiness log](../AUTHORING20_READINESS.md). The earlier Noble APK matrix predates these additions; final artifacts must identify their actual source commit.
