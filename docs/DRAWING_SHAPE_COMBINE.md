# Combine drawing shapes

November 7 — C098 adds **1 source feature; 0 separate fixes; 0 app scaffolding**. The four operations belong to one shape-combining workflow. Shared source total: 97/500; 59 outcomes beyond the 38-workflow batch baseline. Native packaging and real-device acceptance remain pending.

In Arrange → Combine shapes, select 2–8 visible unlocked rectangles with sharp corners, ellipses or diamonds. Preview the covered union, front-shape cutout, common intersection or odd-overlap area. Insert creates a separately grouped set of editable paths beside the originals and brings it into view. One drawing Undo removes the insertion. Keep originals only discards the preview.

The front selected shape supplies colors and opacity; a cutout uses the front remaining base. Results have solid fills and crisp outlines. Ellipses become 96-segment polygonal curves, disclosed before insertion. Text, photos, arbitrary paths, framed objects and rounded rectangles are refused with guidance. A preview keeps the selection captured when it started; any document change invalidates it.

Clipping runs in an offline Blob worker with geometry/style from the selected shapes only. It receives no text, photos, query or source file location. The app terminates the worker on cancellation, timeout, tool closure or hidden-document notification and revokes its Blob URL. The five-second deadline, queue/sweep limits, bounded output complexity, 500-object cap and 1 MiB draft preflight stop the whole operation rather than inserting a partial result. Output dimensions, style index, paths and source identity are checked before preview or insertion.

Holes use one connected fill path plus separate ring outlines. Area checks validate the hole bridges; the renderer does not draw individual fill triangles. A reload regression caught the pinned renderer changing a zero-width invisible stroke to its default width. Generated fill paths now use a supported width with a transparent stroke. Reload checks allow only null-to-empty binding-list normalization and compare all other stored fields.

## Evidence

- 208 drawing tests pass: 189 retained and 19 new geometry/task families. They cover all operators, winding/area, rotated shapes, ellipse approximation, multiple holes, unsupported/protected sources, invalid geometry/styles, placement/object limits, sanitized worker input, exact source capture, cancellation, deadlines, stale results, malformed output and resource disposal.
- 67 required Node tests and 8 drawing-asset tests pass. Drawing-only npm ci and an isolated web bundle pass; its source / 269 generated-file manifest matches. Esbuild reports the unavailable parent React Native TypeScript config. This is not a mobile typecheck or full-app build.
- The maintained browser test uses the actual editor, canvas selection, Blob worker and PNG/SVG renderers with synthetic shapes. It checks all four insertion operators, original preservation, grouped editable paths, preview/discard, one Undo/Redo, reload and exact portable project content.
- The exported 180×180 transparent PNG has 14,160 interior shell pixels at red alpha 128 and 5,184 interior hole pixels at alpha 0. No bridge seam or double alpha appears in those sampled regions. The 388×180 SVG raster has the expected white hole and pink shell. A separate 120×120 six-bar union preserves four transparent holes, shell alpha and intersection alpha. Rotated ellipse/diamond input also inserts and undoes.
- Actual UI edits reject stale proposals; selection-only changes do not retarget them. Delayed worker admission in the harness verifies cancel, timeout, panel closure and tool switching. Object 500 and nearly 1 MiB drafts refuse atomically. Rounded rectangles and empty intersections leave the drawing exact. Three viewports pass without horizontal overflow: 320×768, 390×844 and 844×390. Screenshots and generated PNGs were reviewed. Browser runs report zero page errors and external requests.

Run the browser regression against an isolated bundle:

```sh
HONORABLE_DRAWING_OUTPUT=/tmp/noble-shape-boolean-web node Honorable-mobile-app-main/mobile-react-native/creation-web/build.mjs
NOBLE_SHAPE_ASSETS=/tmp/noble-shape-boolean-web node Honorable-mobile-app-main/mobile-react-native/creation-web/test/shape-boolean-preview.cjs
```

Playwright must have its Chromium installed; this session uses `PLAYWRIGHT_BROWSERS_PATH=/tmp/noble-shape-browsers`. Local evidence is retained under `.context/shape-boolean/`, including test logs, source/artifact hashes and browser results. The image handoff receiver is simulated. Android WebView Blob-worker/CSP behavior, touch/accessibility/device performance, native photo delivery, configured full-app integration, APK and store acceptance remain unverified. Tracked Android drawing assets are deliberately unchanged until the owner's broad build batch.

## Open-source components

Pinned polygon-clipping 0.15.7 (MIT) performs clipping; Earcut 3.0.2 (ISC) identifies hole-bridge edges. Splaytree 3.2.3 (MIT) is the only new transitive package; all pre-existing lock records stay unchanged. Exact pinned notices are bundled under `creation-web/THIRD_PARTY`, including Splaytree's notice published in its package readme. The existing robust-predicates notice remains. Primary project references: [polygon-clipping](https://github.com/mfogel/polygon-clipping), [Earcut](https://github.com/mapbox/earcut). The implementation keeps original media, on-device search/ranking and billing behavior intact.
