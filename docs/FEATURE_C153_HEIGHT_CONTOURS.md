# C153 — Editable height contours from photo brightness

One new source workflow: Photo tools → Height contours converts an opaque embedded photo's encoded brightness into editable vector contours at chosen levels. Registered at source145/500 after verification. **1 new workflow;0 separate product fixes;0 scaffolding.** Experimental source/browser implementation, excluded from the pinned138 native build.

The user chooses an area-sampling grid,1–8 unique integer levels1–254 and a title, reviews all contours or an individual level, then inserts all reviewed levels beside the preserved photo. Each level has its own nested group inside one artwork group, with editable title/legend and one Undo. Filtering the review does not omit levels from insertion. Existing photo bytes, geometry, opacity, angle and flip remain unchanged. Saved project JSON, SVG, Redo and reload retain the inserted geometry and metadata.

## Defined geometry and limits

Source dimensions8–1024 per side and fully opaque alpha255 are required. Each sample grid side is8–64 and cannot exceed that source side. Each disjoint bin averages every contained source pixel's rounded encoded luma `(54R+183G+19B)/256`; sample positions are the mean source pixel indices. This is encoded brightness, not inferred physical elevation, depth or GIS data.

Every sample quad splits on a fixed northwest–southeast diagonal into two linear triangles. Samples equal to a level belong to its high side. Canonical shared-edge and exact-vertex keys join crossings; duplicate segments and zero-length isolated point contacts are omitted. Branch nodes terminate polylines, with remaining cycles traced separately. Collinear points simplify with1e-9 tolerance and coordinates round to6decimals. Open and closed paths are retained. Every chosen level must lie strictly inside the sampled range and produce a contour; flat, missing-level or excessive-complexity results refuse the entire operation. No silent level dropping. Limits300paths/24000aggregatepoints.

The diagram scales its longest source side to640 workspace units, preserves aspect ratio and includes wrapped title/legend. Local path origins start at their first point; nested level groups and `nobleContourLevel` metadata survive recovery. Later manual edits can change the relationship to the original brightness field.

## Implementation

`photo-contours.mjs` owns sampling, fixed-triangle interpolation, graph assembly and vector layout. The local single-use Blob worker transfers a private source copy. Its client validates dimensions, levels, range, path counts, coordinate bounds, closure and metadata before accepting the result. Decode plus worker share a30second deadline. Stop, hidden document, Escape, source/settings revisions, unmount, transport errors and late results terminate/revoke owned resources. Editing then undoing still invalidates the captured source; reopening captures a fresh target.

The editor checks object/draft capacity and storage before atomic insertion, preserves the original and creates one history step. No dependency, upload, native permission or ranking/credit/ledger change.

## Verified evidence

-19 new focused tests: independent area averages over uneven17×19 bins; analytic planes; closed Manhattan-cone contours; independent barycentric interpolation on a saddle; exact-level ridge deduplication; flat/alpha/level/complexity refusal; max1024×512 input; metadata/vector coordinates/Unicode wrapping; private copies, malformed worker replies, cancellation, deadlines and cleanup.
- All666 drawing tests and70 Node product/tooling tests pass,0failures/skips.
- Actual browser passes independent analytic coordinates for64square and1024×512 planes, closed cone vectors and full closed-scene reload equality; per-level review and insertion of all levels; exact inserted world points/nested groups/metadata; original bytes/geometry; quota/500object-capacity refusal; one Undo/Redo/reload/portable JSON/SVG.
- Invalid duplicate/outside/blank levels, flat photos, partial alpha and excessive complexity refuse without source mutation. Actual worker Stop/hidden/Escape termination and late real-worker result after edit→Undo are checked. Escape terminates exactly one additional worker (document counter1→2).
-320/390/844px layouts have no horizontal overflow and44px action targets; no page/CSP/external-request errors.

Immutable proof: `.context/noble-fast-web/milestones/C153/`. Production-input SHA256 `9d645a6bee70ef0a9bb7c141d49678a3684fbd19d3884500da13d45cee572a60`. Isolated current web bundle/manifest verified against source. All271 pinned138 asset hashes and manifest remain unchanged.

Two initial browser fixture failures are preserved separately: the first attempted a new operation without reopening a correctly invalidated target; the second compared a document-scoped termination counter against a lifetime constant after reload. The fixture now reopens between independent stale-target scenarios and checks an exact per-operation termination delta. Product guards, deadlines and geometry assertions were unchanged. These are new-workflow test completion,0 separate product fixes.

The readable register's stale143 headline and missing C142 entry were reconciled with its authoritative144 JSON baseline during C153 registration:1 documentation consistency correction, not a user-facing feature.

## Remaining acceptance

No full-app/Gradle/APK build was run for this feature. Android WebView/rendering/editing/export, mobile memory/performance, accessibility/TalkBack, real-device lifecycle and physical-height/GIS accuracy are unverified. Source/browser evidence does not establish device or store readiness. Pinned138 native packaging belongs to Noble-main and excludes this feature; its current outcome must be checked independently. Goal500 stays active, following roughly100-outcome native-build batches.
