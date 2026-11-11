# C145 — Unwrap a circular band into a straight PNG

One distinct source workflow: Photo tools → Unwrap circular band. Registered at source140/500 after final verification. **1 new source outcome;0 product fixes;0 scaffolding. One browser-fixture correction is counted separately.** This workflow is excluded from the pinned138-workflow native build.

## User outcome

Mark a center on the supplied image or enter fractional source pixel coordinates, choose inner/outer radii, start angle and angular direction, then review an exact-size rectangular PNG of the full circular band. The original embedded source and canvas geometry remain separate. A reviewed strip can be inserted as one Undo step or handed off as the exact unpadded PNG.

This rectifies an explicitly chosen annulus; it differs from four-corner flat-document rectification(C072) and a camera view of a spherical panorama(C141). It assumes flat circular artwork viewed head-on. Perspective/curved-surface distortions may remain; no OCR or physical scene reconstruction is provided. Only the flattened result pixels persist; the ring recipe is not stored.

The strip goes through360degrees from left to right;0degrees points right,90down. Direction can reverse. Rows go from inner toward outer radius. Pixel-center samples omit duplicated angular endpoints, so opposite columns are adjacent samples across the seam rather than exact duplicates. The full circle must fit inside the source pixel-center bounds and the radii must differ by at least1pixel. Source/output sides are8–1024pixels. Output dimensions are exact; a PNG above192KiB is refused without a smaller substitute.

## Implementation

`photo-annulus.mjs` validates the source/ring/grid and maps each output pixel to `theta=start+direction*2*pi*(x+0.5)/width`, `r=inner+(outer-inner)*(y+0.5)/height`, then source `(cx+r*cos(theta),cy+r*sin(theta))`. Bilinear interpolation weights RGB by alpha before unpremultiplication, preventing hidden colors from bleeding into visible output. Rounded zero-alpha pixels become canonical transparent black. Invisible selected bands refuse even when other source pixels are visible.

`photo-annulus-task.mjs` admits a fresh single-use Blob worker, privately snapshots/transfers RGBA, validates all returned source/output/center/radius/start/direction metadata, full pixel-view shape and actual visible count, and terminates/revokes resources on success, stale owner, cancellation, timeout or transport failure. `photo-annulus-image.mjs` binds bounded decode, worker and exact PNG encoding to an overall30second lifetime and hidden/abort guards.

`PhotoAnnulusTool.jsx` maps actual pointer coordinates through the source SVG screen transform into pixel-center coordinates. Source circles/crosshair/start ray are review guides only. Numeric keyboard controls provide the same choices. Changing settings/hidden/Stop invalidates the proposal. Editor ownership sticks invalid after an actual drawing edit even if Undo restores geometry. Existing exact-copy admission preserves reviewed bytes and refuses quota/capacity failures. Escape bypasses the owned busy gate to cancel a held verifier. The source photo's canvas crop/rotation/flip/opacity do not alter the source sampling grid.

## Verified evidence

- 20 new focused cases:11kernel/geometry and9worker/protocol. Analytic quarter turns/full gradient, random RGBA against an independent two-stage premultiplied bilinear oracle, hidden-color alpha, exact1024×512 grid, source snapshot isolation, bounds/blank/cancel/metadata/foreign/deadline/transport cleanup pass.
- All 602drawing cases and70Node product/tooling cases pass, no skips. Logs are retained in `.context/noble-fast-web/milestones/C145/`.
- Actual browser: real pointer center becomes30,32 and its entire64×16strip matches independent coordinates; every pixel in each64×16 gradient strip matches independently calculated source coordinates for clockwise,90degree start and reverse direction. The source has canvas flip/rotation/opacity, and remains unchanged. Separate alpha-edge output is `[0,0,255,128]`, excluding hidden red.
- Exact 1024×512 output is103037bytes. Four independent analytic gradient samples match and the exact PNG bytes are stored alongside the unchanged original. Noisy1024JPEG source→1024square exact target refuses192KiB without mutation or resampling.
- Invalid/blank fields, outside circle, insufficient band thickness and transparent bands refuse. Raw native handoff refusal/retry, settings/hidden/Stop/actualworker termination/quota, original bytes/geometry/exact insertion/oneUndo/Redo/reload/portableJSON/SVG, held bitmap verifierEscape and actual edit→Undo stale approval refusal pass.
- 320/390/844px layouts fit horizontally, actions retain44px touch heights, no page/CSP/external request errors. Retained atlas shared-editor regression now passes with corrected source seeding, and its complete proof is preserved under `retained-atlas-corrected/`. The final annulus rerun also passes with the same reset plus a complete pixel check immediately after the real pointer mark.

Immutable final source/bundle/test/log/browser evidence: `.context/noble-fast-web/milestones/C145/final/`. Earlier proof and the retained failure/corrected atlas proof remain alongside it. Production-input SHA256: `586d4d5df8f505aba9d07868f9777647f7679fd8adb8ba9fbdf3dbda5bfaf7a3`.

## Limits and preserved boundaries

Synthetic browser data only. Actual circular artwork/photo quality, Android WebView/provider/nativePNG/photo-editor/save-copy, gesture/accessibility/TalkBack, mobile memory/performance and device/store behavior remain unverified. Exact PNG handoff is browser-simulated; receiving native editor/export may resample if the user changes its settings. Whole-drawing exports can pad/resample the strip. Artifact presence, full native build and device behavior are separate claims.

No new dependency, uploads, search/ranking/holdout/benchmark, credits/catalog/server ledger changes. Tracked138drawing assets remain pinned to their prior manifest and are not regenerated per feature. The next broad build follows the owner's roughly100-source-outcome batch cadence; the500goal remains active.

### Browser-fixture correction

The first retained-atlas run stopped at its large-image stage because actual map/source IDs still referred to the earlier small fixtures. Its screenshot/body/stack/receipt are preserved under `retained-atlas-seed-failure/`. The test had written replacement local storage while the old app document was still alive, consistent with an autosave/seed race; exact timer ordering was not instrumented. The corrected reset first unloads the app by navigating to a same-origin page without app code, seeds the fixture, reloads, and asserts actual restored source IDs and bytes before proceeding. The unchanged product passes the complete retained atlas test with these stronger setup assertions. The same reset is used in the annulus fixture. This is **1 test-fixture correction across2scripts**, not a new user outcome or product fix. No expected image, assertion threshold or application deadline was relaxed.

All271pinned138asset hashes remain unchanged at this milestone. The final product input hash matches the initial passing annulus run; only browser fixture setup/checks changed during the regression investigation. No application deadline, pixel expectation or size limit was relaxed.
