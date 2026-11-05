# C070 — Local suggested photo framing

**1 new experimental source workflow; 0 separate fixes/scaffolding.** The register has 69 source workflows,31 added beyond the batch baseline 38. The next broad native/APK build target remains approximately 138. Four aspect ratios are parameters, not separate features.

## User result

Objects → Suggest crop finds a content-aware framing suggestion for an embedded photo. A square, portrait 4:5, wide 16:9 or vertical 9:16 crop can be reviewed before explicit Apply. Keep current framing makes no scene edit. The crop fits inside the old photo box and preserves its center, file identity, rotation, flip and opacity. One Undo restores the prior crop. Editable projects and recovery keep the original embedded bytes plus the crop.

The algorithm uses the full embedded photo, even if the object already has a manual crop. It is a local visual heuristic and does not promise good framing or recognize identities. Show, unlock and ungroup the photo first. PNG/JPEG copies must have at least 32 pixels on each side and remain within the existing 1024 px/byte bounds. No image or query is uploaded.

**SVG and editable projects retain the full embedded photo behind the crop. PNG is flattened visible artwork.** The tool states this before Apply. Cropping is not a secure-redaction workflow.

## Open-source integration

[Smartcrop 2.0.5](https://github.com/jwagner/smartcrop.js) is pinned by package.json/package-lock.json. Its MIT notice is retained in creation-web/THIRD_PARTY/SMARTCROP-LICENSE.txt and the offline bundle. The encoder analyzes only decoded local canvas data; minScale and maxScale are 1 so it considers the largest fitting window for the chosen ratio. No new native API, media file or document schema is added.

`photo-framing.mjs` validates source dimensions and crop bounds, builds the fitted crop patch, skips exact no-op reapplication, fences the async decode/analysis phases, and renders a bounded preview with the object's flip/rotation/opacity. `PhotoFramingPreview.jsx` manages review/Cancel/request lifetime. The editor captures its identity, encoded scene and source file; it rejects stale work before a proposal is used or applied.

## Verification

- **118 model families pass, including 5 new framing families.** Geometry fitting/center/source/appearance, no-op, prior crops, invalid geometry/dimensions, protected/stale targets and ratio geometry are covered.
- The actual browser flow uses a synthetic 800×600 image with off-center detail. It finds a 600×600 crop at x 168,y 0, keeps source bytes and protected artwork exact, does not edit before Apply, and supports Cancel, one-step Undo/Redo, no-op reapplication, Save/reload and project JSON.
- Actual proposed preview and exported PNG/SVG preserve the chosen source-region content ratio and normalized colored-region centroids. This tests the same region and flip/rotation across preview and exports. Source opacity and other element fields remain exact.
- The real browser service rejects at all three async fences: before decode, after decode and after analysis. No page/CSP/external-request errors occur; phone widths 320/390/844 and the bundled MIT notice are checked.
- The existing photo-replacement browser regression passes on the new bundle. It retains its invalid-input/quota preservation, source/geometry/Undo/recovery/export checks.
- Logs, source hashes, synthetic exports, screenshots and measured values: `.context/noble-fast-photo-framing/verification.json`. The isolated preview/browser cache is under `/tmp/noble-fast-framing-tools/`, not packaged Android assets.

The initial test recovery assertion reloaded immediately after an unsaved Redo, before autosave. The harness now saves explicitly before testing recovery. No production change was made for that harness failure. A missing old temporary browser installation was restored in this task's own directory without deleting source, media or other-agent caches.

## Limits

No Android WebView/SAF/native lifecycle, real-device, performance or crop-quality acceptance; no full React Native/Gradle/APK build or production-readiness claim. These are source and local-browser checks. Source protection, native search/ranking and credit/ledger rules are unchanged. Existing dependency audit findings remain outside this feature's scope.
