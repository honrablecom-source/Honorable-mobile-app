# Third-party notices

Honorable includes third-party runtime libraries and model assets. Their copyrights
and license terms remain with their respective owners; this repository does not
change those terms.

- AndroidX, Jetpack Compose, Room, and Android Gradle tooling — Android Open Source
  Project / Google; Apache License 2.0 components.
- ML Kit text recognition and image labeling — Google; distributed under the terms
  accompanying those SDK artifacts.
- ONNX Runtime — Microsoft; MIT License.
- Coil — Coil contributors; Apache License 2.0.
- TinyCLIP model family — Microsoft Research and the specific model distributor.
  The bundled artifact is pinned to the `TinyCLIP-ViT-8M-16-Text-3M-YFCC15M`
  variant. Confirm and retain the upstream model card/license with every release.

Release engineering must generate a complete dependency-license report and review
the upstream TinyCLIP model card before public distribution.

## Browser Android UI assets

- Roboto variable font: Google Fonts / Roboto authors, SIL Open Font License 1.1. Source: https://github.com/google/fonts/tree/main/ofl/roboto . Bundled license: `android-app/test-lab/web-test-shell/Roboto-LICENSE.txt`.
- Material Design Icons (Rounded): Google, Apache License 2.0. Source: https://github.com/google/material-design-icons . Bundled license: `android-app/test-lab/web-test-shell/Material-Icons-LICENSE.txt`. SVG paths are packaged in `android-icons.js` for local use.


## TinySDF distance transform (v2.0.7)

The photo sticker generator adapts the squared Euclidean distance-transform routines from [Mapbox TinySDF v2.0.7](https://github.com/mapbox/tiny-sdf/tree/v2.0.7), licensed under BSD-2-Clause, Copyright (c) 2016-2024 Mapbox, Inc. Noble ports that small algorithm to Kotlin; it does not embed TinySDF's browser/font runtime. Full upstream notices are retained in `android-app/app/src/main/assets/TinySDF-LICENSE.txt` and the adapted source header in `PhotoSticker.kt`. The React Native Android app includes that shared asset directory through its existing Gradle source set, so both Android apps receive the notice without a duplicate asset path.

## Delaunator photo mosaics

The offline drawing workspace uses [Delaunator5.1.0](https://github.com/mapbox/delaunator) (ISC License, Copyright(c)2026 Mapbox) for editable photo triangle mosaics, with robust-predicates3.0.3 (Unlicense) for geometry predicates. These versions were already resolved in the drawing lockfile; direct Delaunator usage is now pinned. Unmodified notices are in `mobile-react-native/creation-web/THIRD_PARTY/DELAUNATOR-LICENSE.txt` and `ROBUST-PREDICATES-LICENSE.txt`; drawing asset generation copies them into THIRD_PARTY. Packaged Android assets still need refresh at the owner's broad build checkpoint.
