# C179 — Manual three-point photo registration

One new source workflow, registered at167/500: Photo tools → Photo registration maps a source photo onto a chosen reference photo's exact pixel grid from three corresponding pixel-center landmarks. Existing blending/reference-tone tools explicitly do not align images. Surface mockup maps artwork to a four-corner surface and is a different workflow. This is manual affine registration, without subject detection, automatic matching, perspective/lens/parallax/nonrigid correction or guaranteed real-scene alignment quality.

## Geometry

Both full embedded source/reference grids must be8–1024pixels/side. Each numbered source/reference pair lies within its own grid; points may be fractional. Triangles must have absolute signed double-area≥max(4,0.005×longest-edge-squared). This rejects tiny and nearly collinear setups. Opposite orientation requires explicit mirrored-registration consent.

The reference triangle's inverse barycentric map yields a source coordinate for every output reference-grid pixel, including extrapolation outside the landmark triangle. The output source sampling is an affine transform with six finite coefficients, supporting translation/rotation/scale/shear. Alpha-weighted bilinear interpolation handles partial transparency and excludes hiddenRGB; outside-source coverage fades to transparent and fully transparent output RGB canonicalizes tozero. This is geometric resampling; there is no budget-driven output resizing.

The UI exposes three numbered pairs, source/reference image taps and fractional coordinate fields, a distinct-reference picker and explicit mirror permission. Default corner pairs describe full grids, not detected subjects. Source/reference drawing crop/rotation/flip/opacity do not affect embedded-pixel landmarks. The reference overlay/opacity are guide-only; the exact PNG contains only the warped source. Both images decode and validate natural dimensions under an overall30second cancellation/deadline guard.

## Insertion and ownership

Reviewed output must fit192KiB at the reference grid or refuses. All-transparent output refuses. Revisions, hidden-page, Stop, Escape, unmount, reference/landmark/mirror changes and sticky drawing edits invalidate pending proposals. Overlay/pair selection are guide-only and retain reviewed bytes. BlobURLs revoke.

Exact PNG insertion/handoff uses existing verified-copy admission, source guards and capacity/storage checks. Insertion anchors beside both chosen originals, retains their bytes/geometry and adds one independent photo/oneUndo. Local `nobleRegistration` metadata records source/reference file IDs and dimensions, paired landmarks, inverse matrix, mirror flag and pixel-center convention. Project recovery and JSON retain the flattened PNG and landmark recipe. SVG exports the rendered PNG. The tool does not restore editable raster layers or automatically reopen the recipe.

## Verification and limits

8focused tests PASS: every paired anchor and independent known affine coordinates, whole-source tent sampling with independent signed-area barycentric oracle for everyRGBAbyte, mirrors/differentreferencegrid/hiddenRGB, exactidentity/transparentextrapolation, privateinput/landmarkcopies/progress/first-finalcancel, all4194304identityRGBAbytes at1024square, invalid/ill-conditioned/outside controls, actual display-grid clickmapping. All777drawing and70Node server/project/configuration tests PASS. Isolatedbundle/manifest pass; actualbrowser PASS:5fullRGBAidentity/fractionaltranslation/rotation/mirror/differentrefgridcases; actualsource+reftaps/blank/degenerate/mirrorconsent/refdedup/missingref/overlayPNGidentity/rawretry/URLrevoke/source+recipe/UndoRedo/reload/JSONSVG/quota8files500objects/heldencoderStopHidden/verifierEscape/staleedit→Undo/320/390/844px44px. Max1024squareall4194304RGBAbytes/transparentborder/exactstoredPNG+recipe/reload pass; natural2398062bytePNGrefused at192KiB/referencegrid1024square, no budgetresizing. No page/CSP/externalerrors. Retainedblend browser regression also passes against the final currentbundle; logs/receipt retained with C179 proof.

No perfeature fullnative/APK build, physicaldevice/WebView/provider/accessibility/memory/performance/store or realphotoalignment-quality acceptance. Preserve pinned native138 drawing assets and private search/ranking/holdout/benchmark/credit/catalog/ledger invariants. Main's separately verified batch138debug APK does not contain this later source.

Immutableproof `.context/noble-fast-web/milestones/C179/`; sourceSHA256 `9a5455804eeff3374d19f493a09373e8a294db61f9bb24cf6f02781767007d9e`. All271frozen138assetbytes/SHAunchanged; manifestSHA256 `9a7dff9a2c8195a8c69ebd2231f968e22f5081d4b35da2cdedf15710c30c519a`. 1newsourceworkflow/0separateproductfixes/scaffolding counted.
