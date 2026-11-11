# C142 — Selection-order multi-page photo PDF

**1 new experimental source workflow; 0 separate fixes; 0 scaffolding.** Registered after source verification. Excluded from the pinned source-138 build; no APK or device result is claimed.

Library selection → Create photo PDF reviews two to eight distinct photo originals in selection order. Preparing creates a separate private PDF, one complete photo per fitted A4 page, with white backgrounds and 24-point margins. Landscape photos use landscape pages; square/portrait photos use portrait pages. Decoded copies use at most 1024 pixels per edge. Transparent areas become white. Original bytes and edits remain read-only. Review shows page order, original/copy dimensions and file size before explicit Open.

Opening consumes the prepared token, rechecks current account/original bindings and SHA-256, then offers only that PDF through an Android chooser with read-only URI permission. Originals are not granted to the receiving app. A PDF app is required; actual selection, rendering, saving and printing remain unverified. This is a temporary app-cache copy, subject to eviction and cleanup of this workflow's files older than one day. Keep a durable copy through the chosen app if needed.

## Bounds and ownership

The bridge validates current Library ID/URI/type bindings and account/foreground state. It reuses exclusive Library-read admission, cancellation, source limits of 32768 pixels per edge/100 megapixels, 64 MiB per stream and 256 MiB total, sampled decode/EXIF normalization and a 96 MiB initial heap-headroom check. Preparation has a checked 60-second deadline; framework/provider calls may remain blocked until returning, so instant physical cancellation is unproven.

Output is capped at 16 MiB. The writer latches cancellation, invalid ranges, over-limit and destination failures, preventing later commitment of partial failed output. Document/bitmap resources close or recycle on failure and failed files are deleted. Pause/destroy/invalidate and JS close/background/account/selection/unmount clear matching ownership; late results are disposed. Handoff takes the lease before launch, preserving the explicitly opened copy through background cleanup. Failed opening needs new preparation rather than token reuse.

The Library parent checks live selection references as well as rendered props, rejecting a retained callback after same-tick selection change. Pending work synchronously holds parent admission. Page metadata is bound to owner/selection, preventing stale page-count rendering.

## Verified evidence

- **68 JVM tests pass: 13 new and 55 retained photo-kernel/budget/lease cases.** New tests cover 2,000 independent aspect ratios, page orientation/centering/margins/no crop, dimension/count bounds, mixed writes and independent digest, exact/over limits, invalid ranges, empty output, cancellation and latched destination failure.
- **444 RN cases pass in all 33 suites at original timeouts.** Focused checks cover 201 cases across ten suites, including 24 new panel cases and two actual Library integration cases: exact ordered bindings, explicit prepare/open, same-tick admission, retained callbacks, owner/source/order/background changes, late results, failure/retry, invalid native pages and cleanup. An initial cold-transform run had one five-second timeout (23/24); unchanged warm rerun, focused regressions and full pass are retained.
- Typecheck exits 0; lint has 0 errors and 38 existing warnings.
- **15 production Kotlin files plus five typed boundary/routing fixtures compile against real Android 37.0, React 0.87 and AndroidX core references using Kotlin 2.2.** Exact current new/retained routing/lifecycle bodies match the compiled source contracts and hashes. Existing account/database/editor boundaries are typed fixtures, not Android runtime proof.

Evidence: `.context/noble-main-c142/verification.json`, `api-check/verification.json`, `all-photo-kernels.log`, `full-jest.log`, focused/duplicate UI regressions, typecheck/lint logs and input hashes.

## Limits

Actual Bitmap/EXIF/provider behavior, generated native PDF bytes/image quality/colors, file/resource/memory performance, viewer permissions, Android lifecycle under load, accessibility, physical devices and store readiness remain unverified. No archival-resolution, HDR/profile-preservation, OCR, editable-page or source-metadata-retention claim. This is source-built/API-compiled experimental work, not a full app build.

No per-feature whole-app/Gradle/APK build, uploads, holdout inspection, benchmark, search/ranking, credits, catalog or server-ledger changes. Separate packaging recovery after the container reset continues only for frozen source138.
