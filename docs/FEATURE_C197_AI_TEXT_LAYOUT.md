# C197 — Experimental AI photo lettering to editable layout

**1 new experimental user-facing source workflow;0 separate completed fixes/scaffolding. Source183/500;317remaining.** Source/API/host checks accepted; no actual Android OCR inference, physical-device, current integrated app/APK or store acceptance.

## User outcome

Photo editor → On-device AI → AI text layout reads Latin-script lettering from the complete edited preview using the actual existing bundled OCR SDK. The owner corrects every desired line and explicitly chooses which to use; none are selected automatically. A full rendered preview and all selected wording require a fresh unchecked confirmation before one-use insertion. It appends white paper and independently editable black horizontal sans-serif text objects. Original photo, all existing objects and every other edit remain underneath/unchanged. One actual native Undo restores the prior edit. Existing Objects controls can edit/remove the new paper/text.

OCR can miss or misread lettering. Fonts, colors, rotation and original typography are **not** inferred/recovered; positions are approximate. The complete corrected text must fit the supported native text frame/font sizes without ellipsis or vertical clipping, otherwise correction/cropping is required. Text may need adjustment after resizing/export. This is editable reconstruction, not deletion of the original text pixels: underlying photo/layers remain in editable work.

The setup states local Latin OCR, bundled model and possible SDK diagnostics; no photo or query upload is required. Transparency is white-matted on an owned SDK snapshot. A separate numbered source reference helps correction; its numbers are never inserted. The final layout supports full horizontal/vertical pan and1×/2×/4× zoom. Back retains corrections/choices but invalidates old Apply and resets confirmation.

## Bounds and ownership

Existing32-object limit is preserved, reserving one paper slot: at most31selected lines minus old object count. No old object is deleted, overwritten or silently dropped; no automatic choices. OCR batch≤100lines,1000UTF16characters/raw line,16000total;101or malformed/outside geometry refuses the batch. Selected corrected text uses existing200UTF16/three-line/valid-Unicode rules. Source image≤8192edges/6M pixels; normal editor decoding remains unchanged. IDs are new/validated; original object references and non-layer edits are retained exactly.

Geometry clips real SDK boxes to the working image and normalizes output-relative frames. Fitting uses the exact float-normalized frame/integer StaticLayout width used by PhotoObjectPlan. Native TextPaint/StaticLayout matches the existing renderer: sans normal/left alignment/zero tracking/no pad/≤3lines; minimum actual one-pixel font is honored. Complete wording is checked, never replaced with truncated text or guessed detections.

Parent ownership captures controller identity, exact edit identity, source/bitmap/mask, foreground/generation, mode, local session scope and protected-original metadata. Setup and work are rejected when stale. Apply consumes its token before calling the parent, rechecks ownership/identity, then uses the existing native commit/history path once. Failed approval keeps edits/history and does not retry.

Stop/pause/destroy closes controls and discards late output. The SDK input/client are retained until the actual GoogleTask completes, even after Stop/90second timeout. Parent busy ownership remains until **both** worker and SDK settle. The numbered reference/preview are independent; only their owned bitmaps are recycled. A completed obsolete callback cannot clear a newer controller's ownership. SDK work may finish after Stop; instantaneous native cancellation is not claimed.

## Implementation and source scope

Production: PhotoTextLayout.kt, PhotoTextLayoutTypography.kt, PhotoTextLayoutReader.kt, PhotoTextLayoutControls.kt and narrow PhotoEditorActivity additions. Tests: PhotoTextLayoutTest.kt, PhotoTextLayoutTypographyTest.kt, PhotoTextLayoutReaderTest.kt, PhotoTextLayoutControlsTest.kt.

Actual default reader calls existing bundled ML Kit Latin16.0.1 TextRecognition with real line text/bounding rectangles. Injectable results are only test seams; production does not create substitute OCR. No new dependency/manifest/model asset/Gradle/RN bridge/search/ranking/credit/catalog/server-ledger edits. Native on-device search preserved; no personal-media/query uploads, Seran holdout or expensive search benchmark.

Accepted C195Root is byte-identical outside one field, two methods, two entries and two lifecycle close calls. Other6C195source/test files,6neural model/license/provenance assets and271pinned RN drawing assets remain unchanged. Current source stays separate from frozen138 builds. Next broad native/APK checkpoint~238; no per-feature full app/native/Gradle/APK build.

## Verified evidence

**38current distinct cases:**22core/font/realGoogleTask cases plus16actualSDK34native-graphics controls/parent cases. The initial37combined run passed66.394s; after adding a stronger actual-entry ownership case, all16current UI cases passed164.845s on the exact same product jar. The unchanged22component cases previously passed204.238s. No assertion/deadline weakened.

Core cases cover immutable batches/plans, source-order corrected selection, exact existing-edit preservation, capacity/paper reservation, dense/invalid Unicode/text/geometry refusal, bounded frames/IDs, no partial output, cancellation/fitter failures. Native cases cover complete fitting/shrink/refusal, exact normalized frame, actual layer rendering and real GoogleTask success/failure/cancellation/pending Stop/timeout/late completion/cleanup.

UI/parent cases cover no work before explicit start, every candidate correction/none-selected, decoded full layout matching every pixel of independent **actual PhotoRasterRenderer** output, fresh one-use confirmation, Back/retained controls, no silent text truncation, owner/pause/queued Stop/SDK input settlement, no OCR on stale setup, rejection/cache-free behavior, alpha ownership and layer capacity. Actual Guest entry opens correctly; eight changes (generation/mode/source/edit instance/bitmap/mask/scope/protected originals) prevent any OCR before work. Actual parent commits exactly one history entry, clears redo and rejects a second use; actual navigateHistory Undo restores the previous edit and renders it. Equal-but-distinct base/failed ownership preserve busy/history.

**42unchanged prior regressions PASS140.829s:**16C195neural-upscale controls,9C170subject-asset controls and17C193text-privacy/Reader/Root cases on the exact new Root. Actual whole current PhotoRoot/new controls compile API37 against real SDK dependencies; SDK34Robo is not API37runtime or a physical phone.

A separate generated-data probe compiles actual current PhotoSessionCodec with the real planner/model types:32objects/31corrected200UTF16 supplementary-Unicode strings round-trip exactly in44069bytes; old50history entries trim to2 while nearest Undo/base remain; wrong scope/stale identity fail. Its fitter is injected: this proves serialization, not glyph fit or OCR.

Initial SDK cancellation fixture failure is retained: its cancellation-token callback was queued on paused main looper, so the10second wait expired. Delivering that callback and asserting complete+canceled fixed only the fixture; production jar/source and deadline stayed unchanged. All current UI/parent tests pass without product changes from pushed16224c1.

Evidence under `.context/noble-fast-ai/C197/`: acceptance.json; controls-receipt-1.json; sdk-overlay-receipt-0.json; root-wip-receipt-0.json; regression-receipt.json; recovery-probe-receipt.json and retained full logs. Root API jar SHA256 `4aaee37515394d32b42260534db55b6799aaa71235d5529f4d1a97c85f46ff38`. Nine source/test aggregate SHA256 `39dc16c509008b9915a5a5f89d9812a285ff3c13d7242c52b6bfb132b7636b0d`.

## Remaining limits

Real SDK OCR inference/accuracy, Android device/memory/thermal/performance, physical selection/pan/accessibility/lifecycle, original permissions/actual filesystem recovery/export and current integrated app/APK/store acceptance are unverified. SDK/controls fixtures use generated bitmaps and injected OCR payloads; original SDK calls are API-bound but not run on a phone. No privacy-redaction or original-font recovery guarantee. Source/API/host evidence must not be promoted to device readiness. Goal500/genuine-AI priority continues.

Primary API: [ML Kit Android text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Existing bundled dependency is already present in both editor hosts; no new model setup/download required.
