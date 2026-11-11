# C155 — Dimensioned editable cuboid net

One new source workflow: Layouts → Box net creates six connected cuboid faces, seven beveled glue tabs, distinct cut/fold lines and a nominal millimetre dimension key. **1 new source workflow;0 separate product fixes;0 scaffolding.** Source/browser implementation; no physical fabrication acceptance. Registered at source146/500 after verification.

## User result and scope

Enter width, height and depth10–200mm, glue-tab depth2–20mm, all in0.1mm steps, and a title1–48visible characters. Tab depth cannot exceed one third of the smallest box dimension. Preview the entire labeled net before inserting it as one group beside preserved artwork. Ungroup to edit individual lines/labels; one Undo removes the complete net. Project recovery, JSON and SVG retain the editable geometry and nominal dimension key.

The four side faces form a width/depth/width/depth strip of chosen height. Top and bottom width×depth faces attach to the first side face. Three beveled tabs attach to each lid, and one seam tab attaches to the strip's far side. Each flap bevel is half the chosen tab depth. Six face letters correspond to the dimension key. Solid black edges cut; dashed gray edges fold. Face/tab ownership is saved as per-line metadata.

This is explicitly zero-thickness geometry: no material thickness, kerf, bend radius, adhesive clearance or structural/loading allowance. Physical assembly has not been tested. The nominal drawing scale is4workspace units/mm; export/printer scaling can change physical dimensions. Both preview and inserted artwork disclose checking scale against the dimension key before cutting. No actual-size printing guarantee or automated fabrication handoff is implied.

This is a fabrication-layout outcome, distinct from C104 dimension callouts on existing artwork or C052 tabular layouts.

## Implementation

`box-net.mjs` builds six face polygons and seven outward trapezoid tabs. Unique polygon edges shared twice are folds; edges shared once are cuts. The resulting planar disk has28cut segments and12fold segments, with no overlapping polygon interiors. Line origins use their first endpoint, supporting negative local deltas. Fixed1px strokes, explicit face-label bounds and supported text properties survive renderer recovery.

`BoxNetTool.jsx` provides dimension controls, complete SVG review and explicit insertion. Any parameter change discards review. The editor captures a source snapshot with sticky invalidation: editing then undoing still requires a fresh preview. Before insertion the geometry is rebuilt and compared with the reviewed plan. Existing shared atomic worksheet publication checks draft/object/storage capacity before mutation and adds one history step. The complete net has51editable objects; originals remain separate. Generation is synchronous and bounded to13polygons/40line segments, with no worker or unbounded search.

## Evidence

-7new focused tests pass. Independent edge enumeration verifies a single degree-two outer cut cycle, disk Euler characteristic, exact28/12edge topology and no overlapping face/tab interiors using separating axes across48extreme/uneven dimension combinations. Independent shoelace areas match all six faces and seven beveled flaps.
- Independent rigid3Dfolding rotates adjacent faces90degrees about shared edges, confirming eight corners each shared by three faces, six perpendicular outward face normals and exactly the requested cuboid extents for four cube/unequal/extreme cases. This is mathematical zero-thickness closure, not physical assembly testing.
- Geometry-to-plan scale/coordinates, edge ownership and stroke styles, explicit label bounds, max bounds, invalid dimensions/tabs/control characters, Unicode wrapping, determinism and caller-state isolation pass. A separate exhaustive check accepted all1901valid tenth-mm dimension values10.0–200.0.
- All673drawing tests and70Node product/tooling tests pass,0failures/skips.
- Actual browser independently reconstructs expected cut/fold/tab edge sets without importing the generator, compares every rendered edge and nominal key, then every inserted world coordinate, saved owner metadata and style. Uneven42.1mm depth and maximum200mm cube pass.
- Source photo bytes/geometry, grouped51object insertion, atomic quota and500object-capacity refusal, oneUndo/Redo/reload, portable JSON/SVG, invalid/blank inputs, settings invalidation and actualedit→Undo stale-review refusal pass. Maximum-size complete-scene reload passes.
-320/390/844px layouts fit horizontally and action targets are44px. No page/CSP/external-request errors.

Immutable proof: `.context/noble-fast-web/milestones/C155/`. Production-input SHA256 `9f2c50e99883d5ebfad022f6805f98568564df4fec4bdb244e386c766d12ccbc`. Isolated current web bundle manifest matches source. All271pinned138asset hashes and manifest remain unchanged. Retained maze actual-browser regression also passes; its terminal receipt and independent tree/route/source/recovery checks are saved alongside this proof.

## Remaining acceptance

No full-app/Gradle/APK build was run for this workflow. Native WebView/editor/export, fonts/accessibility/TalkBack, mobile memory/performance/lifecycle and real-device behavior remain unverified. Print sizing, material suitability, glue clearance and physical assembly remain unverified. The source138native packaging owned by Noble-main excludes this feature; its latest board report says session36989failed when the Gradle daemon disappeared. No APK/device/store success is claimed.

No dependency, permission, network upload, search/ranking, Seran holdout/benchmark, credit/catalog/server-ledger changes. Peer C144privacy/native source and all dirty work are preserved. Goal500 stays active under the owner's roughly100-outcome build cadence.
