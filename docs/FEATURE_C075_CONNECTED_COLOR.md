# C075 — Remove a connected background color

**1 new experimental source workflow; 0 separate fixes/scaffolding.** Seed coordinates and tolerance are parameters of one workflow. No full app/APK build or device acceptance is claimed.

Objects → Remove connected color lets a person tap a simple background color, review removal to transparency and insert a separate local PNG copy beside the original. Accessible integer coordinate fields also select the source pixel. RGB tolerance 0 matches exactly; higher values allow each source color channel to differ from the seed by that amount. Selection spreads up/down/left/right, stops at transparent pixels and leaves disconnected matching areas intact. This is manual color selection, distinct from the existing AI person cutout.

The tool uses the full embedded source before object crop, rotation, flip or opacity. It preserves the original and protected artwork; Keep original only cancels without mutation. A checkerboard preview shows transparency. The new image is flattened, hard-edged and may retain halos. Existing Undo/recovery/export work. To export only the new cutout, the panel explains Projects → Image copies → Use transparent image background → Use selected artwork, retaining PNG in the photo editor. Default white-background image export and SVG background settings remain unchanged.

## Open-source reuse and safeguards

Pinned [magic-wand-tool 1.1.7](https://github.com/Tamersoul/magic-wand-js) supplies the MIT-licensed scanline flood fill. Its full notice is retained in `creation-web/THIRD_PARTY/MAGIC-WAND-LICENSE.txt` and the offline bundle; no transitive runtime dependency is added. Noble adapts its four-connected RGB selection with a visited-mask barrier for fully transparent pixels. Selected RGBA values become zero; every unselected decoded RGBA value is retained in the pure kernel. A transparent seed or removal of all visible artwork is refused.

Source dimensions/byte length, integer coordinates and tolerance are validated. Dimensions stay within 8–1024 px on each side. Decode, selection and PNG-encoding lifetime fences reject stale source/scene/editor identity, and component request/unmount guards prevent stale previews. The bounded library selection itself is synchronous; cancellation cannot interrupt the middle of that call. Device latency remains unmeasured. Publication uses the existing guarded photo pipeline with object/byte checks and durable storage before scene publication; a storage failure leaves the drawing unchanged and allows retry. Existing project limits may further reduce the new copy's dimensions. No media is uploaded, and native search/ledger behavior is untouched.

## Verification

- 135 drawing model families pass, including 6 new families. An independent breadth-first reference agrees with the library wrapper for 160 seeded mixed RGB/alpha images. Exact retained pixels, enclosed matching islands, diagonal separation, transparent barriers, faint alpha, inclusive seed-relative tolerance, invalid input/full removal and the 1024 px bound are checked.
- Actual browser point picking, invalid coordinates, preview, Cancel and insertion pass. A synthetic 64×64 source yields exactly 3,072 cleared pixels, 960 unchanged red pixels and 64 unchanged enclosed white pixels. Source bytes and original crop/flip/rotation/opacity plus a locked transparent object remain unchanged.
- Synthetic quota failure/retry, Undo/Redo, save/reload, project JSON, embedded SVG and selected transparent PNG export pass. The export retains transparent corners; the PNG was visually inspected.
- Five real-service source-lifetime fences reject; transparent seeds and complete removal also reject in the browser. Phone widths 320/390/844, bundled MIT delivery and no external/page/CSP errors pass.
- Evidence, source hashes, logs and synthetic artifacts: `.context/noble-fast-photo-wand/verification.json`. Dependency audit remains at the existing 12 findings (2 low, 1 moderate, 9 high); magic-wand-tool has no finding. The isolated bundle's existing missing React Native base-tsconfig warning does not stop bundling.

No packaged Android asset refresh, Android WebView/native bridge/lifecycle, real-device speed/quality, full-app integration or production-readiness claim. These are source and local-browser checks.
