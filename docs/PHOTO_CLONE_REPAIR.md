# Manual clone repair — C106

Choose clean texture in an embedded photo, then paint over a small unwanted area or place a stamp by coordinates. Preview the repaired pixels and insert a separate flattened PNG beside the original. Each stroke samples the original photo, so later repairs cannot accidentally become source texture. Brush hardness and opacity control the blend. Undo removes one insertion; the source remains available for another attempt.

This is one new user-facing workflow: shared source count104/500, batch66 beyond38;0 separate fixes and0 scaffolding. Source selection, numeric stamps, brush controls, stroke Undo and preview are parts of that workflow, not extra features. There are no new package dependencies. Stroke simplification reuses the pinned MIT `points-on-curve` package.

The tool operates on the full embedded image before crop, rotation, flip or opacity. It does not recognize objects, synthesize missing content or match lighting. Transparent source samples and movement outside the source paint nothing. Every stroke starts at its own chosen source point and follows its target movement. The preview makes these limits visible before insertion.

## Bounds and ownership

Inputs are local embedded PNG/JPEG copies, 8–1024 pixels per side, within the existing 192 KiB embedded-photo budget. Repairs allow up to 16 strokes, 128 simplified points per stroke and 1024 total points. Raw pointer paths are bounded at 1024 points and validated before simplification; an oversized or invalid stroke is refused whole. Radius is 2–96 source pixels. The UI keeps existing stroke settings fixed when the user changes the next brush.

An owned Blob worker receives a transferred private pixel copy. It samples immutable original pixels with premultiplied bilinear interpolation and source-over compositing. One maximum coverage mask per stroke prevents opacity buildup from repeated points. Separate strokes can build opacity. The final unique RGBA comparison rejects unchanged results.

Each preview has a request id, progress bounds, source-generation checks, cancellation and a 30-second total deadline. Malformed worker results and duplicate/foreign replies cannot publish. Hiding the page discards previews; stopping a preview retains accepted strokes. Source edits invalidate ownership permanently, including an edit followed by Undo. Worker, Blob URL, bitmap/canvas and listener cleanup have focused checks.

Insertion uses the existing guarded photo pipeline and storage preflight. A storage failure permits retry without changing the drawing. Cancel before insertion commits preserves the original scene and files. While an old decoded bitmap is held, global busy ownership remains until that operation finishes. Object and draft-size limits refuse the insertion atomically.

## Verified evidence

- All 365 drawing tests and 75 Node/asset tests pass. The 51 new focused cases cover clone pixels, request lifecycle, PNG lifecycle and stroke admission.
- Actual Chromium Blob-worker checks cover exact pixels, transfer of a private copy while retaining the original buffer, duplicate admission, progress, live cancellation, stale source, deadlines, no-op errors, constructor failure and cleanup. Actual PNG decode/worker/encode pixels and late-encoding cancellation pass.
- Actual editor checks cover source picking, mouse painting, exact repaired pixels, numeric stamping, stroke Undo, pointer cancellation, hidden-preview discard, stopping while retaining strokes, quota refusal/retry, separate insertion, original content/bytes, one Undo/Redo and reload. Cancel during a held real output bitmap leaves the scene/files unchanged.
- Actual edit/Undo stale-preview refusal and 500-object/near-1 MiB draft atomic refusal pass. Selected PNG export contains the repaired colors; SVG embeds the repaired PNG and portable project JSON retains its exact bytes.
- The isolated offline web bundle verifies 271 generated assets against current source. The tracked Android drawing assets remain deferred with the native build batch.
- Existing photo-tools browser checks pass nine routes, locked protection, native disclosure keyboard semantics, reduced motion, touch navigation, 44-pixel controls and phone widths, without page/CSP errors or external requests.

Actual touch source picking and painting produce the same checked pixels. Clone controls fit 320/390/844-pixel widths with no horizontal overflow and at least 44-pixel action targets. Instantaneous automated drag transitions suppressed a compatibility click; the test uses 60ms drag phases and settled layout. First-tap preview passes without a second-tap workaround. The ineffective touch-action UI experiment was removed. Local receipts and full logs are retained under `.context/photo-clone-repair/` and private `/tmp/noble-clone-*` directories. All media fixtures are synthetic.

## Acceptance limits

These checks exercise Chromium and local source/host code. They do not establish Android WebView behavior, physical touch/keyboard/accessibility, provider permissions, device performance, configured full native compilation, APK integration or store acceptance. The missing parent React Native TypeScript configuration warning is not mobile typecheck/lint/Jest evidence. No native/APK build occurred for this workflow; the owner's broad build checkpoint remains approximately 138 source outcomes. No personal media upload, holdout/benchmark, search ranking, credit catalog or server ledger change is involved.
