# C169 — Adaptive document cleanup

One new source workflow: Photo tools → Document cleanup converts an unevenly lit page photo into an opaque black-and-white copy, with local window radius, sensitivity and light-text polarity controls. These controls count together as one workflow. Registered at 160/500 after focused, broad and actual-browser proof. No separate product fixes or scaffolding are counted.

## Pixel contract

The full embedded source grid is preserved at 8–1024 pixels per side, before drawing crop, rotation, flip and opacity. No automatic resizing, page detection, perspective correction, OCR, redaction or readability guarantee is provided. Review faint marks and large solid regions, which can disappear.

Encoded RGB luminance uses weights 54/183/19 over 256. Light-text mode reverses luminance before compositing source alpha over white and rounding to an integer. Hidden transparent colors therefore cannot become black marks. Output pixels have full opacity and are black at or below the local threshold, white above it.

For radius 1–63 pixels, the square neighborhood is clipped at source boundaries. With local mean m, population standard deviation s and sensitivity k (5–50 percent), the threshold is `m × (1 + k/100 × (s/128 − 1))`. Boundary windows contain only actual source pixels. Uniform black stays black; uniform midgray or white becomes white. This encoded-color approximation is intended for manual review, without a claim that it preserves every document mark.

Integral gray sums use Uint32 (maximum 267386880), squared sums use Float64 (maximum 68183654400). The private source copy, gray plane, two integral planes and output occupy about 21 MiB at the maximum grid, excluding decode/canvas/caller/PNG overhead. Phone memory and performance remain unverified. Async processing yields every 32 rows in both stages and checks ownership/cancellation before capture, between chunks and after the final yield.

## Review, ownership and recovery

The image pipeline owns decoding/canvas/listeners/timer and has an overall 30-second deadline. The exact PNG must fit 192 KiB; an oversize result refuses without resizing. Stop, hidden-page, unmount and settings changes cancel/discard previous review. Preview Blob URLs are revoked. Source/drawing changes invalidate the proposal permanently, including an edit followed by Undo.

Insertion uses the existing verified exact-copy admission path, preserves original bytes/geometry, checks photo/object/draft capacity and publishes one new image in one Undo step. Exact PNG handoff uses reviewed bytes and supports refusal/retry. Later photo-editor or whole-drawing exports may resample. Escape can cancel a held insertion verifier without publishing an image. Project recovery, JSON and SVG retain the inserted PNG.

## Verification and limits

Seven focused tests pass: an independent brute-force local-window oracle with two-pass variance across 24 control combinations; counts/source preservation; uniform and hidden-RGB cases; private async capture/progress; early/final cancellation; invalid inputs; every output RGBA byte for 1024-square partially transparent alternating stripes. All 740 drawing tests and 70 Node server/project/configuration tests pass. Isolated drawing bundle and manifest pass.

The initial actual-browser run passes four complete 64-square pixel comparisons, both polarities/window extremes, invalid/blank controls, exact PNG handoff refusal/retry, original geometry/bytes, Blob URL revocation, held encoder Stop/hidden cancellation, held verifier Escape, quota/8-file/500-object refusal, Undo/Redo/reload, JSON/SVG, sticky edit→Undo invalidation, and 320/390/844-pixel layouts with 44-pixel action buttons. No page/CSP/external-request errors. The expanded browser run also passes maximum 1024-square PNG insertion/reload and every one of its 4194304 decoded RGBA bytes. Oversize refusal uses an explicitly synthetic oversized PNG Blob, not a claim about naturally compressed binary PNG size.

All 271 frozen native138 drawing asset hashes remain unchanged (manifest SHA256 `9a7dff9a2c8195a8c69ebd2231f968e22f5081d4b35da2cdedf15710c30c519a`). This later source is excluded from that native build. No new full native/APK build, physical-device, WebView, provider, accessibility, document-readability or store acceptance. No personal uploads, search/ranking/benchmark/holdout, credits/catalog/ledger or dependency changes.

Immutable proof: `.context/noble-fast-web/milestones/C169/`; source SHA256 `68127bd54321db3b7d21bfa6de76df170d78aa27e60d3b1a735d43831dae795e`. Retained radial browser regression also passes against the final bundle; its logs/receipt are retained with C169 evidence.
