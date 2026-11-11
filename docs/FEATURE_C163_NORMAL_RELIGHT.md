# C163 — Directional normal-map relighting

One new source workflow: Photo tools → Normal-map relight uses a supplied RGB normal map to shade a matching color photo, reviews an exact PNG and preserves both inputs. Registered at source154/500 after focused, broad and actual-browser verification. One workflow, not one count per lighting parameter.0separate product fixes/scaffolding.

## User result and limits

Choose another distinct visible/unlocked/ungrouped matching8–1024pixel embedded photo as a normal map. Enter whole azimuth0–359degrees, elevation0–90, direct strength0–200percent and ambient0–100percent. Optionally reverse green for image-down normal maps. Full embedded grids are used before canvas crop/rotation/flip/placement/opacity; inputs must already align.

Azimuth0points right,90up,180left,270down. Elevation0lies in the image plane and90points toward the viewer. MapRGB decodes red as +Xright, green as +Yup and blue as +Zviewer, then normalizes each vector. A byte128only approximates zero: [128,128,255]is approximately flat, whereas [128,128,128]normalizes to a diagonal. Green reversal changes the Y sign. Map alpha blends the fully lit/clipped color with the original unlit color; source alpha is retained exactly. Hidden map RGB under zero alpha cannot influence lighting. Source-transparent RGB canonicalizes to zero.

This interprets supplied normal data rather than estimating depth/geometry from an ordinary photo. It multiplies existing colors, including any baked lighting. There is no shadow casting, reflection, ambient occlusion, geometric reconstruction, physically calibrated rendering, linear-light/HDR/ICC grading or guaranteed real-surface appearance.

Review the encoded PNG, insert its exact bytes beside the unchanged original or explicitly hand off the reviewed PNG to the photo editor. Both inputs remain; one Undo removes insertion. Output over192KiB refuses at the original grid, without adaptive resizing. The output is flattened, not a saved live-lighting scene. Later native or whole-drawing export can alter alpha/size/colors. This is distinct from C121, which creates normal-map data from height artwork; C163 consumes such directional data to produce lit color artwork.

## Defined math and ownership

The normal uses integer components`2*channel−255`, with optional negative green, divided by their Euclidean length. These odd-valued8bit components cannot produce a zero vector. The light is`(cos(elevation)cos(azimuth), cos(elevation)sin(azimuth), sin(elevation))`. Intensity percent is`ambient+direct*max(0,normal·light)`. Each encoded source channel is multiplied, clipped to255, then mixed with its unlit source value according to map alpha and rounded once. Percent-scaled accumulation avoids avoidable ambient-only half-rounding loss. This explicit encoded-RGB approximation is the output contract.

`photo-relight.mjs` validates settings and privately copies both source planes, with32source-row batches and current checks around yields, including the last. `photo-relight-image.mjs` captures options, owns both image decodes/canvas/PNG encoder and an overall30second deadline. Stop/hidden/source or settings changes/unmount/errors/deadline invalidate ownership and release listeners/handlers/canvas/timer. No partial output is published.

`PhotoRelightTool.jsx` owns revisions/abort/preview Blob URLs. Settings discard review and revoke its old URL. Editor source invalidation survives edit→Undo, requiring reopening. Shared exact-copy admission verifies output bytes/grid/capacity before atomic publication. Escape can cancel a held insertion verifier despite the busy panel gate. No new dependency or native permission.

## Verified evidence

-10new focused tests pass: cardinal light axes/unit length; independent spherical-coordinate cosine oracle over323mixed RGBA/normal pairs across six direction/strength cases and both green conventions; map alpha/clipped-lit mixing/source-alpha preservation; hidden RGB, ambient-only half-rounding, identity/black settings, green reversal/back-facing normals; both private copies, exact async/sync equality, progress/cancellation including the last yield; all-pixel1024×512analytic output/bounded scheduling and invalid settings/buffers.
- Initial hand-written expected blue in the clipped-lit/half-map case used the fully lit value instead of mixing it with the unlit source. Correct expectation is100after mixing, not150. Kernel and independent spherical-oracle assertions were unchanged. Initial fixture failure is retained at `.context/noble-fast-web/milestones/C163-initial-fixture-clipped-mix/`.
- All712drawing and70Node product/tooling tests pass,0failures/skips. Actual browser also passes eight complete64square RGBA comparisons against the independent spherical oracle after canvas storage quantization: default, grazing/cardinal directions, green reversal, unlit identity, black illumination, clipping and another map.
- Candidate selection excludes duplicate source bindings and wrong-size photos; missing maps/invalid direction/strength/blank values refuse. Settings invalidate review/revoke the previous URL. Exact PNG handoff refusal/retry preserves reviewed bytes. All existing source byte sets/geometry/angle/opacity/flip stay unchanged.
- Held PNG encoder Stop/hidden, quota refusal/retry, exact stored PNG, oneUndo/Redo/reload/portableJSON/SVG, held insertion verifierEscape with no publication and actualedit→Undo stale-review refusal pass.320/390/844px layouts fit horizontally and action targets are44px; no page/CSP/external-request errors.
- A1024×512result compares all2097152decoded RGBA bytes against an independent analytic expectation with partial source and map alpha, then inserts exactly the reviewed PNG. Real1024square noisy JPEG inputs produce a2763731bytePNG and refuse at192KiB with unchanged1024×1024encoding dimensions and no scene mutation. This is a natural encoded-size refusal without adaptive resizing.

Immutable proof: `.context/noble-fast-web/milestones/C163/`. Production-input SHA256 `4615667643bc8d59d5c5f0f934e438422b4cb135141900292c5e15048fb9a012`. Current isolated web bundle/manifest matches source. All271pinned138asset hashes and manifest remain unchanged. Retained current-bundle chroma-key actual-browser regression also passed; its terminal log and independent matte/source/cancellation/capacity/recovery/max-grid/no-resize refusal receipt are archived alongside this proof.

## Remaining acceptance

No per-feature whole-app/Gradle/APK build or tracked138asset refresh. Native WebView/bitmap/provider/photo-editor/export, mobile memory/performance/accessibility/TalkBack/lifecycle/device/store and real material/physical lighting quality remain unverified. Native138integration and mainC150work are separately owned. No personal-media/query upload, search/ranking/holdout/benchmark, credits/catalog/server-ledger or dependency changes. Goal500 remains active under the owner's batch-build cadence.
