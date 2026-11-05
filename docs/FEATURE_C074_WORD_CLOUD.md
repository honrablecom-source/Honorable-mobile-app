# C074 — Editable weighted word cloud

**1 new experimental source workflow; 0 separate fixes/scaffolding.** Weight values and individual words are inputs to one workflow, not additional features. No full app/APK build or device acceptance is claimed.

Layouts → Word cloud turns 2–24 explicit `word or phrase | weight` rows into a locally arranged composition. Weights from 1–100 control relative text size. Preview includes every row, and Insert creates grouped ordinary black text objects. Close panel cancels without changing artwork. Ungroup allows individual movement and styling; editing text afterward can change spacing. Existing artwork is retained, one Undo removes the insertion, and existing recovery/project JSON/SVG/PNG exports work.

Labels may contain up to 24 visible characters. Compatibility-normalized, case-insensitive duplicates, directional controls, invalid weights and malformed rows are rejected. Long phrases may not fit: the tool refuses incomplete layouts rather than silently dropping words. It does not interpret HTML, fetch URLs, infer frequencies or upload input. Text stays in the editable project as ordinary artwork.

## Open-source reuse

Pinned [d3-cloud 1.2.9](https://github.com/jasondavies/d3-cloud) supplies the spiral placement/bitmap collision engine. Its BSD-3-Clause notice and dependency d3-dispatch's ISC notice are retained in `creation-web/THIRD_PARTY/D3-CLOUD-LICENSE.txt` and the offline bundle. Noble supplies a rectangle-mask canvas adapter rather than interlocking glyph masks, leaving room for independently editable text boxes. The font matches the workspace's built-in Helvetica family, rotation is disabled, and a fixed local random sequence makes repeated layouts deterministic.

The service waits for fonts, processes layout in bounded time slices, has an eight-second timeout, stops on AbortSignal and checks captured editor/scene identity before and during work and before insertion. The component aborts on unmount and rejects stale requests. The existing scene preflight rejects a whole insertion exceeding object/byte bounds. No native interface, search engine or ledger change is involved.

## Verification

- 129 drawing model families pass, including 5 new families for row parsing/weight scaling, normalization/duplicates/controls, size and count limits, ordinary text conversion, and complete-layout validation.
- Actual browser flow validates malformed input, review without mutation, Cancel, all seven words, shared grouping, relative sizes, nonoverlapping actual renderer text boxes and unchanged locked/transparent pre-existing artwork. Undo/Redo, saved-draft reload, editable JSON, SVG text and a nonempty PNG export pass.
- The real service repeats an identical layout, places 24 short words, rejects a non-fitting long-label layout, rejects three stale-source boundaries and a cancelled request. A project with 498 existing objects refuses a seven-word insertion and retains all 498 objects.
- Phone widths 320/390/844 and delivery of both license notices pass; no page/CSP/external-request errors. The PNG was visually inspected. Evidence: `.context/noble-fast-word-cloud/verification.json`, with source hashes, logs and synthetic exports.
- Dependency audit remains at the existing 12 findings (2 low, 1 moderate, 9 high); neither new runtime package has a finding. No force dependency upgrade was attempted.

The initial extended Undo assertion compared renderer revision timestamps/nonces; the corrected harness checks content and geometry while allowing ordinary history metadata changes. The first object-budget fixture assumed 300 objects; it now uses the authoritative 500-object limit. The isolated bundle reports the existing missing React Native base-tsconfig warning but completes. No packaged Android asset refresh, Android WebView/native bridge, real-device performance, full-app integration or production-readiness claim. Source and browser evidence remain separate from a later app build and device acceptance.
