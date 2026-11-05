# C069 — Editable Code 128 barcode labels

**1 new experimental source workflow; 0 separate fixes or scaffolding.** The shared register has 68 source workflows, including 30 added beyond the batch baseline of 38. The next broad native/APK build target remains approximately 138. C068 stays withdrawn because it duplicated an older duotone effect. C069 adds a distinct linear-barcode result, checked against the existing source inventory before implementation.

Layouts → Barcode label creates a Code 128 label from 1–80 printable ASCII characters. Leading and trailing spaces in a nonblank identifier are preserved. An optional one-line caption, up to 60 characters, stays separate from the encoded identifier. Users can edit the grouped bars and caption, Undo/Redo insertion, recover the project locally, and export editable JSON, PNG or SVG. No identifier is uploaded or opened.

This creates Code 128 rather than EAN/UPC product codes or GS1 registration. Editing bar widths, proportions or quiet borders can affect scans. Check a scan before printing.

## Open-source integration

- Runtime encoder: [JsBarcode 3.12.3](https://github.com/lindell/JsBarcode), MIT license. The version and integrity are pinned in package-lock.json. Its copyright and permission notice is retained in `creation-web/THIRD_PARTY/jsbarcode-MIT-LICENSE.txt` and copied into offline bundles.
- Independent test decoder: `@zxing/library` 0.23.0, Apache-2.0, a development dependency. The app entrypoint does not import it.
- Noble uses its existing white/black controls and ordinary Excalidraw rectangles and text. It does not embed a remote generator.

`barcode.mjs` uses the encoder's object API. The plan uses 2px modules, 10-module quiet borders and 96px bars. Dark runs become clean filled rectangles. The white background, bars and caption receive a fresh group ID through the existing insertion pipeline. Repeated insertions get independent IDs and groups. No raw payload or link metadata is attached; the barcode itself remains intentionally decodable. Existing scene validation limits the complete canvas to 500 objects. Invalid identifiers and captions fail before changing the scene. No schema, native search or credit changes were made.

## Verified evidence

- `npm test`: **113 passing model test families, including 6 new barcode cases**. Independent ZXing reads rasterized editable rectangles, not the encoder's bit string. Cases cover numeric compaction, mixed text, punctuation, exact spaces, the 80-character boundary, invalid input, caption constraints, quiet borders, module geometry, determinism and metadata omission.
- `test/barcode-preview.cjs`: actual editor checks at 320/390/844px; form retention; invalid-input preservation; grouped insertion; independent IDs on repeated insertion; one-step Undo/Redo; reload; editable project JSON; preservation of existing artwork; bundled MIT notice; no page, CSP or external-request errors.
- Actual editor exports independently decode exactly `NOBLE-ITEM-042`: PNG 673×362 and SVG 673×363. This is synthetic browser evidence, not printing or real-scanner certification.
- The existing QR browser regression passes on the same bundle, including independent decoding of actual PNG/SVG Unicode exports.
- Source hashes, logs, exports, phone screenshots and decode results are recorded in `.context/noble-fast-barcode/verification.json`. The isolated preview and browser binaries are under `/tmp/noble-fast-barcode-tools/`. Packaged Android assets were not refreshed.
- The npm audit baseline and updated dependency graph both report 12 findings: 2 low, 1 moderate and 9 high. No new finding names were introduced. This feature does not remediate the existing findings.

The first full-browser download failed because the workspace disk was full. Only this task's npm cache was moved to the separate temporary disk, and headless Chromium was installed there. No source, media or other-agent cache was deleted. The isolated harness initially retained the Android font origin; it was corrected to the repository preview server's same-origin config.js behavior before both final browser passes.

## Remaining acceptance and coordination

No full React Native/Gradle/APK build, Android WebView/SAF/native lifecycle, printing, real-scanner or device acceptance is claimed. This is not production-readiness evidence.

The shared SESSION_HANDOFF, WORK_QUEUE, HONORABLE_CURRENT_STATUS and CREATIVE_500_WORKLOG files remain under noble-main's active 783284db claim. Results are recorded here, in the register, README and board without overwriting those files. Fold this entry into the four notes after explicit release. The pre-feature pushed snapshot is 5d04202; subsequent push history is available in Git log.
