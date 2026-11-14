# Noble full editors — integration in progress

Owner scope: the full open-source PhotoCraft image editor and FilmCraft video editor, adapted for mobile. This is separate from the already published five-library raster lab. The upstream projects are independent creative editors, not Adobe's proprietary source.

`upstreams.json` pins the original repositories, exact revisions and bindgen versions. `patches/` contains Noble's neutral theme, replacement branding and prototype phone panel controls. Code remains licensed MIT OR Apache-2.0 under the respective upstream licenses; include all upstream and dependency/font notices when packaging. Restricted ArtCraft branding must not be distributed in modified builds.

`build.mjs` fetches a clean pinned source tree, checks/applies the patch and builds the full locked Rust/WASM engine with Rust1.95.0. It requires locally installed tools and never overwrites an existing source checkout. Use an isolated, empty build directory outside the repository. Engine code retains optimization; large UI crates currently use opt-level0/codegen-units256 while investigating compilation resource terminations. This is not a mobile performance acceptance result.

```
node full-editors/build.mjs image /tmp/noble-image-build
node full-editors/build.mjs video /tmp/noble-video-build
node --test full-editors/host-bridge.test.cjs
```

Building an engine does not package or enable a studio. Real image/video import, editing, project round-trip, rendered export and phone layout must be verified before shipping immutable `creative-editors/image` or `creative-editors/video` asset bundles. Each needs `bundle.json` version1, kind, exact sourceRevision and a files map of byte sizes/SHA256 covering index.html, host-bridge.js, WASM, JavaScript and notices. No full bundle is currently checked in. React entries are hidden when native verification fails.

The Android host loads only its bundled private HTTPS origin. Imports require the user's local document picker; shared exports are separately generated bounded copies. The streaming bridge does not upload media, overwrite originals or claim a share-sheet launch saved a file. Microphone/camera access is unsupported. Local browser recovery storage can survive sign-out. No iOS host, whole-app/device acceptance or new release artifact is established by this checkpoint.
