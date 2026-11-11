# C159 — Alpha-correct photo blend composite

One distinct source workflow: Photo tools → Photo blend combines a chosen pair of matching embedded photos into a reviewed separate PNG, with mode, top opacity and explicit order control. Registered at source149/500 after focused, broad and actual-browser verification. One workflow, not one count per mode. No separate product fixes or scaffolding.

## User result

Choose a distinct eligible matching-size partner and Normal, Multiply, Screen, Overlay, Difference, Darken or Lighten. The opened photo is the base unless Swap base and top is enabled. Integer top opacity0–100multiplies the current top photo's intrinsic alpha. Both full embedded8–1024pixel grids must already align; canvas crop/rotation/flip/placement/opacity are ignored. No registration, source resizing or subject inference occurs.

Review the actual encoded PNG, then either insert its exact bytes beside the original or explicitly hand those same reviewed bytes to the photo editor. Both originals, including bytes/geometry, remain separate. One Undo removes the inserted copy. A PNG exceeding192KiB refuses entirely at the original dimensions; there is no adaptive resizing or hidden recomposition. The kept copy is flattened, not a recoverable blend-layer stack. Later native or drawing export choices may change size, colors or alpha.

## Defined compositing

The source-over blend follows the general two-stage model and separable mode definitions in [W3C Compositing and Blending Level1](https://www.w3.org/TR/compositing-1/#blending). Decoded8bit encoded RGB is used directly, without linear-light/HDR/ICC grading. Overlay selects Multiply versus Screen according to the base channel; layer order matters. The Multiply/Screen/Difference/Darken/Lighten blend functions are symmetric, but applying a reduced opacity specifically to the chosen top layer can make the complete result order-dependent.

The implementation retains integer arithmetic through the final color/alpha division and round-half-up byte rounding. A top effective alpha numerator is `topAlpha*opacity`. Output alpha numerator is `baseAlpha*25500 + effectiveTop*(255-baseAlpha)` over25500. Separable blend terms are scaled by255to keep products integral, below JavaScript's exact-integer limit. Colors combine nonoverlap, overlap and retained-base contributions before dividing by output alpha. Alpha rounding to zero canonicalizes RGB to zero. Transparent hidden RGB cannot tint a visible pixel.

## Implementation and ownership

`photo-blend.mjs` owns validation, both private source copies, integer kernel and32source-row asynchronous batches. Current ownership is checked before and after yields, including the last yield. No partial output is published. `photo-blend-image.mjs` owns both decodes, canvas, exact PNG encoding and an overall30second deadline. Abort/Stop/hidden/current-source/error/deadline/unmount cleanup clears image handlers/sources, canvas/listeners/timer. A pending short batch exits at its next guard.

`PhotoBlendTool.jsx` owns partner/mode/opacity/order revisions and preview Blob URLs; changing settings discards review/revokes the previous URL. Sticky editor invalidation survives edit→Undo. Partner enumeration excludes identical file bindings, wrong dimensions, locked/hidden/grouped/otherwise ineligible photos. Shared exact-copy admission checks complete capacity and PNG integrity before one atomic insertion. Cancel/Escape bypasses the busy panel gate while an insertion verifier is pending.

## Verified evidence

-8new focused tests pass. Every256×256opaque channel pair in all seven modes matches independent byte-space formulas. An independent BigInt exact-fraction oracle applies the two-stage blend/source-over model to323seeded mixed RGBA pairs in each mode at opacity0/1/25/50/99/100; colors, alpha and changed count match.
- Transparent hidden RGB, alpha-rounded-to-zero canonicalization, full/zero opacity, opaque symmetry/order differences, both private inputs, exact async/sync agreement, progress, cancellation including the last yield,1024×512analytic all-pixel output and bounded scheduling pass. Invalid modes/dimensions/opacity/buffers refuse without source mutation.
- Initial floating alpha evaluation differed by one byte at an exact half boundary. Integer accumulation plus exact-fraction reference resolves the ambiguity without relaxing assertions. The initial failure remains in `.context/noble-fast-web/milestones/C159-initial-alpha-rounding/`; this is completion of the new workflow, not a separately counted existing-feature fix.
- All691drawing tests and70Node product/tooling tests pass,0failures/skips. Actual browser also passes11complete64×64RGBA comparisons covering all seven modes, reduced/zero opacity, swapped order and another partner. Each pre-encoding expected pixel comes from the independent exact-fraction oracle; expected pixels then traverse canvas storage quantization before comparison with every decoded output byte.
- Partner options exclude the duplicate original file and a wrong-size photo; missing-pair admission refuses. Settings invalidate review and revoke the previous Blob URL. Exact PNG handoff refusal/retry keeps identical reviewed bytes. Both source byte sets, geometry/angle/opacity/flip and any other existing artwork remain unchanged.
- Held PNG encoding Stop/hidden cancellation, atomic quota refusal/retry, exact stored PNG, oneUndo/Redo/reload, portable JSON/SVG, held insertion verifierEscape with no publication and actualedit→Undo stale review refusal pass.320/390/844px layouts fit horizontally with44px actions; no page/CSP/external-request errors.
- A1024×512analytic blend compares all2097152decoded RGBA bytes, then inserts exactly the reviewed PNG. Two real1024square noisy JPEG inputs produce a2969265bytePNG; the feature refuses at192KiB with unchanged1024×1024encoding dimensions and no scene mutation. This is a natural encoded-size refusal, not an injected oversized blob.

Immutable proof: `.context/noble-fast-web/milestones/C159/`. Production-input SHA256 `11baee0abf4515e59924799e11d3108e63557b8374b35fd81c20b1203052fdab`. Current isolated web bundle/manifest matches source; all271pinned138asset hashes and manifest remain unchanged. Retained current-bundle halftone actual-browser regression also passed; its terminal log and independent tone/area/source/recovery/max400dot/cancellation receipt are archived alongside this proof.

## Remaining acceptance

No per-feature native/Gradle/APK build or tracked138asset refresh. Native WebView/bitmap/provider/photo-editor/PNG/save-copy, real-device lifecycle/memory/performance/accessibility/TalkBack/store and color-managed/HDR results remain unverified. Existing native integration owned by Noble-main excludes this newer workflow. No dependency, native permission, personal-media upload, search/ranking/holdout/benchmark or credit/catalog/server-ledger change. Goal500 remains active under the batch build cadence.
