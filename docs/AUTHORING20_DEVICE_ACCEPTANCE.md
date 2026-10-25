# Authoring20 device acceptance — not executed

No Android phone is connected. This is a test procedure, not evidence of acceptance. Use generated test images/videos and internal builds; keep media on the device. Record the APK SHA-256, device/Android version and actual result for each row.

Start with a square PNG containing colored quadrants and transparent corners, plus a green-background image with a red subject. For video, use two short synthetic source clips with visibly different labels and constant-tone audio. Add two captions to a four-second clip at source ranges 0.500–1.000 and 1.500–2.200 seconds. Save original file hashes before editing.

For each photo test, enter Create & compose, apply, inspect preview, export PNG and reopen the saved copy. Undo the tool before starting the next independent test.

| Photo tool | Expected observation |
| --- | --- |
| Canvas padding | Transparent/solid space surrounds a smaller photo; selected color is correct and aspect follows the padding plan. |
| Photo frame | Colored ring surrounds the image; transparent interior pixels stay transparent on a transparent canvas. |
| Drop shadow | Soft shadow follows opaque image areas and fits the surrounding canvas. |
| Photo reflection | Image is vertically reflected below the original and fades toward transparency. |
| Shape cutout | Selected shape removes outside pixels; PNG retains alpha. Test each shape as variants of one feature. |
| Repeat pattern | The complete image repeats, including its alpha, at the chosen tile count. |
| Kaleidoscope | Detail repeats around the center; source and output colors remain intact apart from the geometric mapping. |
| Perspective poster | Positive/negative settings pinch opposite edges. Zero restores flat projection. |
| Repeated watermark | Entered text repeats at the chosen opacity/color in preview and saved output. Reset removes it. |
| Color-key transparency | Matching green becomes transparent while the red subject remains. Tolerance creates a feathered edge. |

For video, enter Project → Batch editing, review every proposal, apply and preview, export and play the saved copy. Check Undo restores the entire preceding project in one action and leaves soundtrack intact.

| Video tool | Expected observation |
| --- | --- |
| Ripple-delete interval | Remove source offsets 1–2 seconds: retained ranges are 0–1 and 2–4, with no timeline gap. |
| Keep captioned ranges | With zero handles, retain only the two caption ranges. With 300 ms handles, overlapping handles merge. |
| Manual beat-grid cuts | At 120 BPM / one beat, cuts follow a half-second playback grid across sources; footage and order are retained. |
| Stepped speed ramp | Four contiguous source pieces play at 0.5/1/1.5/2x, or the opposite speed order. Captions follow source time. |
| Interleave source videos | Grouped A1,A2,B1,B2 becomes A1,B1,A2,B2; each source's own order stays intact. |
| Word-timed captions | Word groups replace cues inside their original timing range; emoji/non-Latin text remains valid. Timing is estimated. |
| Merge caption phrases | Adjacent phrases within chosen gaps join; distant phrases remain separate and all words remain. |
| Burn in source timecodes | Original-source labels are visible in preview and saved video. At 2x, one playback-second steps through two source seconds. |
| Caption-led clip ducking | With fades off, original tone drops during captions and returns outside them. Separate soundtrack is unchanged. |
| Storyboard titles | One pasted line per clip creates corresponding editable titles in preview/export. Wrong line count fails without changing the project. |

Also check: photo lifecycle recovery with composition plus caption/history; video save/reopen after each recipe; cancel/reopen dialogs; unchanged original hashes; source/destination alias rejection; low-memory handling; accessibility labels and touch targets. Invalid proposals must leave state intact. Existing guest/account isolation, Seran search and credit behavior must retain their separate acceptance checks. These manual checks do not establish iOS parity or store readiness.
