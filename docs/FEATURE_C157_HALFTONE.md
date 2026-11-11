# C157 — Editable one-ink photo halftone

One new source workflow: Photo tools → One-ink halftone samples photo tones over white and creates a white plate with independently editable black dots. Registered at source147/500 after focused, broad and actual-browser verification. One workflow only; dot counts, tone choices and grid parameters are not additional features. No separate product fixes or scaffolding.

## Defined user result

Choose4–20columns and rows, no larger than the8–1024pixel source, and a title1–48visible characters. Every disjoint floor-boundary bin includes every contained source pixel. Its encoded brightness is `(54R+183G+19B)/256`, alpha-composited over white. Mean darkness is rounded to an integer0–255. Every positive-tone cell creates one circle; zero-tone cells are explicitly counted and omit a dot. An all-zero result refuses.

Each square cell is32workspace units. Circle radius is `16*sqrt(tone/255)`, so its area relative to the largest circle equals quantized darkness. This does not claim absolute ink coverage: maximum circles touch neighbouring cells and retain white corners. The square-cell grid determines the output aspect ratio, which can stretch the source. Both controls and inserted notes disclose the approximation, white compositing, quantization and aspect change.

The complete white substrate, dots, title and tone notes are inserted as one editable group beside the original. Maximum400dots/404objects, subject to the existing500object draft limit. Source bytes, crop, rotation, flip and opacity remain unchanged; generation uses the full embedded pixel source. Ungroup to edit dots/labels; manual edits change tonal relationships. One Undo removes the whole plate; project JSON and SVG retain its editable circles and cell/tone metadata.

This implements a one-ink variable-dot separation workflow, distinct from C080 colored triangle mosaics. It introduces source-bin tone separation and dot-area geometry, not a preset on the existing triangle generator. No press calibration, printer dot-gain, screen angle, material behavior or physical print accuracy is promised.

## Implementation and ownership

`photo-halftone.mjs` uses integer-weighted alpha/luma totals, bounded bins and a private source copy. A yielding generator checks current ownership at source-row batches and each grid row. Async processing returns only a complete tone array; a cancelled/stale job never publishes partial dots. With at most1024square source pixels and400bins, processing/memory are bounded.

`photo-halftone-image.mjs` owns source decoding/canvas and an overall30second deadline. Stop, hidden document, Escape, unmount, changed settings/source and decode/error/deadline paths invalidate ownership and release handlers/listeners/canvas/image resources. A pending short yield exits on its next ownership check; there is no worker. `PhotoHalftoneTool.jsx` owns revision/abort/current guards and complete review.

Editor insertion rebuilds the vector plan from reviewed tone metadata, compares it, checks complete draft capacity and storage, then publishes one history step. Sticky source invalidation survives edit→Undo and requires reopening the tool. Circle diameters for the lightest nonzero tone exceed2workspace units, avoiding silently dropped subpixel geometry. White plate and black dots use solid fills/transparent strokes rather than outlines that would change tone geometry.

## Verified evidence

-10new focused tests pass: independent encoded-luma/alpha-over-white averages and exact source coverage over uneven17×19bins; predictable black/gray/white/partial-alpha/hidden-RGB tones; zero-cell counts; every circle area/center/style; max400dot plate bounds;1024×512ramp and8×1024narrow source; private source copies and cancellation at yields; invalid inputs/proofs; Unicode/caller-state isolation.
- All683drawing tests and70Node product/tooling tests pass,0failures/skips.
- Initial actual browser compared all56dot tones/positions/areas successfully, then a fixture summary incorrectly took8positive entries after omitting a white cell, including the next row's first dot. The row summary now filters by row rather than taking a fixed count. Production sampling and per-dot assertions were unchanged. Original failure is preserved under `.context/noble-fast-web/milestones/C157-initial-fixture-row-summary/`.
- The second browser run passed insertion, source protection, Undo/Redo/reload/JSON/SVG, quota/capacity and Stop/hidden/Escape, then the maximum1024square preview correctly hit the original30second deadline. Excessive per-column yields repeatedly serialized/validated the source snapshot. The sampler now traverses source rows across all bins, yielding at most52times at maximum source/grid instead of roughly660. The same independent tone math, all source guards, deadline and maximum-case assertion remain. A new bounded-scheduling test passes; original failure is preserved under `.context/noble-fast-web/milestones/C157-max-source-deadline-failure/`. This completes the new workflow, not a separately counted existing-feature fix.
- Final actual browser passes independent every-bin tone and every-dot area/center, including alpha128, transparent hidden RGB and tone1. All56dots/8white cells of the controlled64square source match; changed4column sampling also matches independently. White substrate and every inserted ellipse bounds/style/cell metadata match review. Grouped atomic insertion, unchanged source photo bytes/angle/opacity/flip, quota/500capacity refusal, oneUndo/Redo/reload/portableJSON/SVG and actualedit→Undo stale-review refusal pass.
- Real asynchronous Stop/hidden/Escape runs cancel before publication. Maximum1024×1024black source finishes under the unchanged30second guard, creates all400dots/404objects, and retains full scene semantics on reload.320/390/844px layouts fit horizontally with44px action targets; no page/CSP/external errors. Fullplate screenshot is retained.

Immutable final proof: `.context/noble-fast-web/milestones/C157/`. Production-input SHA256 `a8051943fbef008f7ccee0b1133d2fceeea31cbe8c706d8cbb00508b6407ef15`. Isolated current web bundle/manifest matches source. All271pinned138asset hashes and manifest remain unchanged. Retained current-bundle height-contour browser regression also passed; its terminal log and independent analytic geometry/source/recovery/cancellation receipt are archived alongside this proof.

## Remaining acceptance

No full-app/Gradle/APK build or tracked138asset regeneration. Native WebView/render/edit/export, real-device accessibility/TalkBack/lifecycle/memory/performance and physical press/print behavior remain unverified. Source138native integration belongs to Noble-main and excludes this workflow. No personal media upload, dependency/native permission, ranking/search/holdout/benchmark or credits/catalog/server-ledger change. Goal500 remains active. Native packaging observations remain separate: main reports old36989failed and a controlled retry55447started; this workflow makes no fresh artifact-success claim.
