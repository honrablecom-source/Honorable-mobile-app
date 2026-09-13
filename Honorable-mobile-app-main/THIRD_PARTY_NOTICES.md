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
