# C167 — Panorama to six labeled cube faces

One new source workflow: Photo tools → Cube faces converts a supplied2:1equirectangular panorama into a complete six-face PNG set with consistent geometry, explicit orientation and recoverable labels/metadata. Registered at source158/500 after focused, broad and actual-browser verification. One workflow, not six counts for six faces.0separate product fixes/scaffolding.

## User result and scope

Choose a whole square face size8–384pixels. Review all six PNGs, then insert the complete labeled set as one group beside the unchanged panorama, or explicitly hand one selected reviewed face to the photo editor. Face order is px/nx/py/ny/pz/nz, labeled +Xright, −Xleft, +Yup, −Ydown, +Zfront, −Zback. Each output is the chosen exact square grid, with no adaptive resizing or dropped face. Every PNG must fit192KiB; any encoding/size failure refuses the complete set before review/insertion. The group must also fit existing8distinct-photo/500object/draft-storage limits.

Only complete-set insertion is offered; one Undo removes all12new images/labels. Exact face bytes, axis IDs, dimensions and`noble-cube-png-v1`convention metadata survive project recovery/JSON/SVG. Identical PNG contents can reuse one stored file while retaining six labeled image objects. The original full embedded16×8to1024×512panorama is used before canvas crop/rotation/flip/opacity and stays intact.

This is a full environment representation, distinct from C141's one arbitrary rectilinear view. It does not establish compatibility with a specific renderer, coordinate loader or GPU upload path. PNG rows run downward; verify orientation/size requirements in the target renderer. Exact face handoff adds no drawing padding/resizing, but subsequent photo-editor or whole-drawing exports can alter colors/alpha/size.

## Explicit orientation and sampling

The face-axis mapping is documented against [OpenGL4.6 table8.19](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf), with an explicit top-to-bottom PNG row convention rather than an assumed loader flip. For face coordinates`u`right and`v`down in−1to1, rays are:

- px:(1,−v,−u); nx:(−1,−v,u).
- py:(u,1,v); ny:(u,−1,−v).
- pz:(u,−v,1); nz:(−u,−v,−1).

Source longitude0is the panorama's middle facing+Z; +Xis longitude+90degrees. Longitude wraps across the last/first columns. Latitude maps +Yto the upper pole and−Yto the lower pole, clamping to endpoint rows. Exact pole-center rays choose longitude0. Output pixel centers sample halfway inside their face texels. Adjacent geometric boundary rays match, but neighboring edge texels sample different interior rays; identical border pixels or seamless filtering in an external engine are not guaranteed.

Sampling uses decoded encoded-RGB bilinear neighbors weighted by source alpha. Hidden zero-alpha RGB cannot tint visible pixels; zero-alpha output canonicalizes RGB to zero. Individual empty faces are retained for partial-alpha panoramas; an entirely invisible panorama refuses. This is8bit color-image reprojection, not HDR radiance reconstruction, mipmap generation or a renderer-ready calibration claim.

## Ownership and insertion

`photo-cube-faces.mjs` validates source/size, captures a private source plane and generates all six bounded buffers, yielding in32face-row batches with current checks including after the last yield. `photo-cube-faces-image.mjs` owns source decode/canvas, sequential encoding and an overall30second deadline. No partial face set is published. Stop/hidden/settings/source/unmount/error/deadline paths release listeners/handlers/canvas/timer. Each encoded face is checked at192KiB without resizing.

`PhotoCubeFacesTool.jsx` owns revisions/abort and all six preview Blob URLs. Settings revoke/discard the whole prior review. Exact-face handoff explicitly selects the corresponding reviewed blob. Editor insertion verifies all six PNGs/dimensions/order, deduplicates stored content IDs, checks complete capacity and publishes all12objects atomically in one history step. Explicit220×35labels/line-height1.25and per-image axis metadata support recovery. Sticky source invalidation survives edit→Undo; Escape cancels a held second verifier without publishing earlier prepared faces.

## Verified evidence

-11new focused tests pass: six canonical axis/pole centers; independent major-axis inverse for every17square interior face ray; all12edges paired and8corner rays shared by3faces; independent periodic whole-source tent reconstruction of every mixed-alpha pixel for8/9/17square faces; seam alpha/hiddenRGB and clamped poles; complete empty-face retention/all-invisible refusal.
- All six384square maximum outputs retain every uniform RGBA byte; bounded72yields, private-source capture, exact async/sync output/progress, first/last cancellation, complete labeled frame bindings/metadata/duplicate-file reuse, strict validation and frozen face definitions pass.
- All733drawing and70Node product/tooling tests pass,0failures/skips. Actual cube browser also passes12complete RGBA comparisons: all six17square and all six9square faces against the independent periodic tent-basis oracle after canvas storage quantization.
- Axis/convention metadata, all six recoverable labels, every exact stored PNG, original source bytes/geometry/angle/opacity/flip, complete12object group, atomic quota/distinct-file/object-capacity refusal, oneUndo/Redo/reload/portableJSON/SVG pass. Selected nzraw-face handoff refusal/retry keeps exactly the reviewed PNG.
- All six prior preview URLs revoke on settings changes. Holding the second encoder and stopping/hiding publishes no partial set. An injected oversized second encoder blob refuses the complete set; this is explicitly a synthetic partial-failure test. Holding the second insertion verifier and pressingEscape publishes none of the earlier prepared faces. Actualedit→Undo invalidates approval. Wrong ratio, blank size, invalid size and invisible panoramas refuse.320/390/844px layouts fit horizontally with44px actions; no page/CSP/external errors.
- All six384square outputs from a1024×512uniform partially transparent panorama compare every589824RGBAbyte per face,3538944bytes total. Identical face PNGs correctly reuse one new stored file while keeping all six image objects/labels; maximum-set full-scene reload passes.
- A natural1024×512noisy-JPEG panorama produces a438494byte384square face and refuses at192KiB on the first encoded face, with unchanged384×384dimensions and no scene mutation. No adaptive resizing or face dropping.

Immutable proof: `.context/noble-fast-web/milestones/C167/`. Production-input SHA256 `16ec820bc7cb6aa24b3d3a2555cf1fe8db83dac727fb4bbb6b84d3a85a396064`. Current isolated web bundle/manifest matches source. All271pinned138asset hashes and manifest remain unchanged. Retained current-bundle radial lens actual-browser regression also passed; its terminal log and independent geometry/source/cancellation/capacity/recovery/max-grid/no-resize refusal receipt are archived alongside this proof.

## Remaining acceptance

No per-feature whole-app/Gradle/APK build or tracked138asset refresh. Native WebView/bitmap/provider/photo-editor/PNG/export, mobile memory/performance/accessibility/TalkBack/lifecycle/device/store and external cube renderer/seam filtering/orientation-loader behavior remain unverified. Native138integration and peerC156work are separate. No dependency/native permission, personal-media/query upload, search/ranking/holdout/benchmark or credit/catalog/server-ledger changes. Goal500 stays active under the owner's batch build cadence.
