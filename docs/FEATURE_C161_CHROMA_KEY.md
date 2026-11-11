# C161 — Global soft chroma key with spill suppression

One new source workflow: Photo tools → Chroma key creates an exact-size transparent PNG from a chosen global key color, with a soft matte and optional keyed-edge spill suppression. Registered at source151/500 after focused, broad and actual-browser verification. One workflow; key choices/parameters are not additional features.0separate product fixes/scaffolding.

## User result and distinction

Enter a#RRGGBBkey or tap the full source image/select source-pixel coordinates. Sampling requires nonzero source alpha and reports the actual decoded RGB key. All matching regions are treated globally, including disconnected background islands and matching foreground details. This is manual color keying, not subject recognition. It differs from C075 connected hard color removal, which flood-fills a tapped connected region and retains disconnected matches.

Tolerance0–128 removes close colors. Softness1–128 creates a smooth transition to full retention, with tolerance+softness≤255. Original alpha multiplies the matte. Spill suppression0–100reduces only a dominant key channel on partially keyed edges; a unique key channel must exceed both others by at least32. Neutral/tied keys disable spill suppression and disclose that result. Fully retained source pixels are unchanged. All-removed and unchanged-opacity results refuse.

Review the actual PNG with a checkerboard that is not part of the result, then insert exact bytes beside the preserved original or explicitly hand off the same PNG to the photo editor. Full embedded8–1024pixel source is used before canvas crop/rotation/flip/opacity. No alignment/resizing occurs. PNGs over192KiB refuse at their original grid; later native or whole-drawing exports may change colors/size/alpha. The output is flattened, not a saved live-key recipe. One Undo removes insertion; project JSON/SVG recover the exact embedded output.

## Defined algorithm

Distance is the largest absolute decoded RGB channel difference from the key. With softness`s`, let`k=clamp(distance-tolerance,0,s)`. The retention numerator is`k²(3s−2k)` over`s³`. Output alpha rounds source alpha times that ratio; zero output alpha canonicalizes RGB to zero. Integer numerator/denominator arithmetic keeps alpha half ties well-defined.

For a partial matte with a unique dominant key channel, the suppression strength is`despill/100*(1-retention)`. Only when that source channel exceeds the other two is it moved toward their maximum using that strength. Integer arithmetic defers division/rounding to the final byte. Other channels and source opacity outside the matte remain intact. This is an encoded-RGB approximation, not a perceptual key, foreground reconstruction or linear-light/color-managed solution. Spill suppression can change desired edge colors; preview with your own artwork before keeping a copy.

## Ownership and implementation

`photo-chroma-key.mjs` owns strict source/settings validation, sampled-color and displayed-point mapping, private pixel/key copies, smooth matte/spill kernels and32source-row asynchronous batches. Current ownership is checked around yields and after the last batch. `photo-chroma-key-image.mjs` captures primitive options before decoding, owns image/canvas/PNG resources and a30second overall deadline, and refuses oversized exact output without adaptive resizing.

`PhotoChromaKeyTool.jsx` owns abort/revision guards, sampled-coordinate controls and preview Blob URLs. Settings/hidden/Stop/unmount invalidate review and release URLs/resources. Source taps use the actual uncropped displayed image rectangle; numeric controls remain an accessible alternative. Editor source ownership is sticky through edit→Undo. Shared exact PNG admission verifies bytes/grid/capacity before atomic publication; Escape cancels a held verifier via the busy-gate exception.

## Verified evidence

-11new focused tests pass. An independent BigInt fraction oracle checks every256key distance across red/green/blue keys, four tolerance/softness spans and three spill amounts with mixed source alphas. Global disconnected matches, removed/softened/spill counts, neutral-key behavior, hidden RGB, half-alpha rounding, full/zero/no-op refusals, strictHEX/source sampling/display mapping and invalid settings pass.
- Private input/key mutation across yields, cancellation including the last yield and all-pixel1024×512analytic output with bounded scheduling pass.
- All702drawing and70Node product/tooling tests pass,0failures/skips. Actual browser passes five complete64×64RGBA comparisons against the independent exact-fraction oracle after canvas storage quantization: default soft key, zero spill, changed matte span, actual source-tapped green key and sampled neutral gray key.
- Actual source tap selects pixel4,4 and reports decoded#00ff00; neutral#808080disables spill explicitly. InvalidHEX/span/blank/transparent/outside samples, all-removed and unchanged results refuse. Settings invalidate review/revoke the old Blob URL. Exact PNG handoff refusal/retry preserves reviewed bytes.
- Exact inserted PNG/source byte and geometry protection, atomic quota/eight-photo/500object refusal, oneUndo/Redo/reload, portableJSON/SVG, held encoderStop/hidden, held insertion verifierEscape with no publication and actualedit→Undo stale-review refusal pass.320/390/844px layouts fit horizontally and action targets are44px; no page/CSP/external errors.
-1024×512output compares all2097152decoded RGBA bytes, then inserts exactly the reviewed PNG. A natural1024square noisy-JPEG source keyed against neutral gray produces a3627955bytePNG and is refused at the192KiBlimit with unchanged1024×1024encoding dimensions and no scene mutation. No injected oversized blob or adaptive resizing.

Immutable proof: `.context/noble-fast-web/milestones/C161/`. Production-input SHA256 `90e22d8cb761acbdaffea02ede0338283981b1df03320a7e6861f92a4f20707c`. Current isolated web bundle/manifest matches source. All271pinned138asset hashes and manifest remain unchanged. Retained current-bundle photo-blend actual-browser regression also passed; its terminal log and independent all-mode RGBA/source/recovery/cancellation/max-grid/no-resize refusal receipt are archived alongside this proof.

## Remaining acceptance

No per-feature full native/Gradle/APK build or tracked138asset refresh. Real studio images/foreground quality, Android WebView/bitmap/provider/PNG/photo-editor/export, mobile memory/performance/accessibility/TalkBack/lifecycle/device/store remain unverified. Existing native138packaging and later mainC148work are separate. No dependency, permission, media/query upload, search/ranking/holdout/benchmark or credit/catalog/server-ledger change. Goal500 remains active under the batch-build cadence.
