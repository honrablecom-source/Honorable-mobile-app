# C072 — Four-corner photo straightening

**1 new experimental source workflow; 0 separate fixes/scaffolding.** Format choices and corner controls are parameters of this workflow. No full app/APK build or device acceptance is claimed.

Objects → Straighten photo lets a person mark the four corners of a flat document or poster, review a local perspective correction, and insert an independent PNG copy. The full embedded source is used before the object's crop, rotation, flip or opacity. The original and other artwork stay unchanged. Keep original only cancels without a scene edit. New copies support the existing Undo/Redo, saved-draft recovery, project JSON, SVG and PNG export pipeline.

The white/black review panel offers direct taps and accessible percentage fields, Reset corners and an estimated or explicitly chosen aspect ratio. Edge lengths cannot establish a document's physical aspect ratio, so the UI explains choosing a result format. This handles a flat quadrilateral, not curved lens distortion. The new copy is flattened and its corner settings are not editable afterward; keep the original to try again. Inputs and output remain within the existing 1024 px bounds; project byte limits may reduce output further.

## Open-source reuse

Pinned [perspective-transform 1.1.3](https://github.com/jlouthan/perspective-transform) supplies the projective matrix. Its MIT notice and the bundled Numeric Javascript MIT notice are preserved in `creation-web/THIRD_PARTY/PERSPECTIVE-TRANSFORM-LICENSE.txt` and the offline bundle. The implementation validates convex clockwise corners, minimum edges/area, finite inverse coefficients, denominator signs and corner round-trip accuracy. Bounded row chunks yield to the browser and check source lifetime. Bilinear sampling uses premultiplied alpha so invisible RGB does not tint visible edges. All processing uses local embedded bytes; no media or queries are uploaded.

The editor captures its identity, scene snapshot and source file, rejecting stale proposals. Insertion reuses the existing guarded photo preparation/publication path: retain a durable draft before publishing, enforce object/byte limits and preserve the original on storage failure. The copy is placed beside the source.

## Verification

- 124 drawing model families pass, including 6 new families: exact identity, independent analytic trapezoid coordinates/pixels, premultiplied transparency, invalid corner geometry, bounds and format behavior.
- Actual browser review uses a synthetic gradient with a prior crop, rotation, flip and opacity. Preview and inserted PNG exactly match independent corner/center expectations. Original bytes and geometry and a locked transparent object remain unchanged.
- Browser coverage includes invalid outlines, Cancel, explicit insertion, synthetic quota failure and retry, Undo/Redo, save/reload, editable JSON, embedded SVG and selected PNG export. Four real-service async lifetime fences reject stale work. Phone widths 320/390/844, license delivery, no external requests and no page/CSP errors are checked.
- Existing photo replacement and suggested-framing browser regressions are checked on the same isolated bundle; see `.context/noble-fast-photo-rectify/verification.json` for final results, source hashes, logs and synthetic artifacts.

The first browser run used the wrong test helper key (`image` instead of `images`); only the harness was corrected. The framing regression harness initially wrote its test-only service into the previous temporary preview directory; its wrapper now writes into this task's isolated preview. The missing React Native base tsconfig warning did not stop the isolated web bundle. These checks do not establish Android WebView/native bridge, lifecycle, real-device speed/quality, full-app integration or production readiness. Packaged Android drawing assets have not been refreshed. Existing dependency audit findings remain outside this feature's scope.
