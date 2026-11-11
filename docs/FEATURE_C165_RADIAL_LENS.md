# C165 — Manual radial lens adjustment

One new source workflow: Photo tools → Radial lens samples a manual single-coefficient radial adjustment around a movable center and reviews a separate exact-size PNG. Registered at source156/500 after focused, broad and actual-browser verification. One workflow; parameter/grid choices are not additional features.0separate product fixes/scaffolding.

## User result

Tap the full source image to select a center or enter fractional source coordinates. Choose integer strength−30to75percent and review known straight lines against an optional straight grid. Positive strength samples outward and may create transparent edges; negative samples inward and omits peripheral source content. Zero preserves geometry. Both signs keep the sampling map monotonic over the supported image domain.

Uses full embedded8–1024pixel dimensions before canvas crop/rotation/flip/opacity. The output remains the same grid, with no automatic edge fill, cropping, fitting or resizing. Source alpha participates in bilinear sampling; samples outside the source fade to transparent. The source crosshair, straight review grid and checkerboard are guides only and never enter the PNG.

Insert exact reviewed PNG bytes beside the preserved original or explicitly hand those same bytes to the photo editor. One Undo removes insertion. The output is flattened, not a reusable live lens profile. PNGs over192KiB refuse at the original dimensions. Later photo-editor or whole-drawing export can change size/colors/alpha.

This is a manual cubic radial warp, not automatic lens calibration, a camera profile, perspective/homography correction or a guaranteed real-camera model. It differs from C072four-corner planar straightening and C145circular-band unwrapping. Judge curvature/content loss using your own source; real-camera correction quality is unverified.

## Defined geometry and sampling

Coordinates reference source/output pixel centers from0to(width−1),0to(height−1). The center stays inside that grid. Let`R²`be squared distance from center to the farthest pixel-center corner. For an output point with center-relative`d`, source sampling is`center+d*(1+strength/100*|d|²/R²)`. This is destination-to-source sampling, not a forward splat. Image bounds outside a one-pixel bilinear fringe contribute zero alpha.

For normalized output radius`r≤1`and coefficient`k=strength/100`, source radius is`r+k*r³`. Tangential scale is`1+k*r²≥0.7`; radial derivative is`1+3k*r²≥0.1`over the allowed strength/domain, so there are no radial foldovers. This bound concerns the defined mathematical map, not lens calibration or retained scene coverage.

Source RGBA is sampled with alpha-weighted bilinear neighbors. Hidden RGB under zero alpha cannot tint visible pixels. Accumulated alpha rounds once; zero-alpha output canonicalizes RGB to zero. Visible colors divide weighted RGB by accumulated alpha and round once. Interpolation is on decoded encoded RGB, not a linear-light/color-managed reconstruction.

## Implementation and ownership

`photo-radial.mjs` validates source/center/strength, privately copies source pixels and processes32source-row batches with ownership checks around yields and after the last batch. `photo-radial-image.mjs` owns decode/canvas/PNG encoding and a30second overall deadline. Stop/hidden/current-source/settings/unmount/error/deadline cleanup releases handlers/listeners/canvas/timer; no partial image is published.

`PhotoRadialTool.jsx` owns center/strength revisions, preview Blob URLs and guide-only state. Changing geometric settings discards review/revokes the old URL; toggling the reference grid preserves the reviewed PNG. Display taps map the uncropped image rectangle to pixel centers, with numeric controls as the accessible alternative. Editor invalidation remains sticky through edit→Undo. Exact PNG admission verifies bytes/grid/capacity before atomic insertion; Escape can cancel a held verifier through the busy panel gate.

## Verified evidence

-10new focused tests pass: independent cubic radius equation/numerical inverse/angular preservation across multiple grids/centers/strengths; derivative bounds over the entire permitted strength range; independent whole-source tent-basis RGBA reconstruction for uneven mixed-alpha sources; hidden-RGB/partial-alpha sampling, identity/fractional centers and positive/negative border behavior.
- Display tap mapping, private-copy async/sync equivalence, progress/cancellation including the last yield, all-pixel1024×512separable edge-coverage oracle/bounded scheduling and invalid source/center/strength/buffer rejection pass.
- All722drawing and70Node product/tooling tests pass,0failures/skips. Actual radial browser also passes five complete64square RGBA comparisons against an independent separable tent-basis reconstruction after canvas storage quantization: positive, negative, zero, fractional off-center and actual source-tapped center.
- Source tap selects center4,4; numeric center12.25,21.5also matches independently. Toggling the straight reference grid preserves both preview URL and exact PNG bytes; its three paired line paths are guide-only. Invalid strength/blank/outside centers refuse; changing geometry discards review and revokes its old Blob URL.
- Exact PNG handoff refusal/retry, original source bytes/angle/opacity/flip/geometry, exact stored PNG, atomic quota/eight-photo/500object refusal, oneUndo/Redo/reload/portableJSON/SVG, held PNG encoder Stop/hidden, held insertion verifierEscape without publication and actualedit→Undo stale review refusal pass.320/390/844px layouts fit horizontally with44px actions; no page/CSP/external-request errors.
-1024×512output compares all2097152decoded RGBA bytes against independent uniform-blue/alpha edge coverage, including202808fully transparent pixels, then inserts the exact reviewed PNG. A natural1024square noisy-JPEG source produces a2049803bytePNG and refuses at192KiB with unchanged1024×1024encoding dimensions and no scene mutation. No injected oversize blob or adaptive resizing.

Immutable proof: `.context/noble-fast-web/milestones/C165/`. Production-input SHA256 `3a365efdeb928cb82880103fcc5ddac37155692b7ef7559ca937acc308425980`. Current isolated web bundle/manifest matches source. All271pinned138asset hashes and manifest remain unchanged. Retained current-bundle normal-map relighting actual-browser regression also passed; its terminal log and independent lighting/source/cancellation/recovery/max-grid/no-resize refusal receipt are archived alongside this proof.

## Remaining acceptance

No per-feature whole-app/Gradle/APK build or tracked138asset refresh. Native WebView/bitmap/provider/photo-editor/export, mobile memory/performance/accessibility/TalkBack/lifecycle/device/store and real-camera/lens-calibration quality remain unverified. Native138integration and peer C154work are separate. No dependency/native permission, personal-media/query upload, search/ranking/holdout/benchmark or credit/catalog/server-ledger changes. Goal500 stays active under the batch build cadence.
