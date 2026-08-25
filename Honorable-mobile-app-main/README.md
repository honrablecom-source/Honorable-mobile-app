# Honorable

Privacy-first native Android and iOS assistant. Memories AI indexes and searches
device media locally; Terms AI explains agreements on-device. Media, OCR,
metadata, embeddings, and sampled video frames are never uploaded.

## Projects

- `android-app`: Kotlin, Jetpack Compose, Material 3
- `ios-app`: SwiftUI project sources
- `ui-previews`: browser-based screen previews
- `release-builds`: honest build/readiness reports (generated binaries are ignored)

Google Sign-In and purchases currently expose integration boundaries and UI only;
no credentials or store configuration are included.

## Linux Android-style verification demo

The Codespaces demo uses the real `test-media/` library and JVM search pipeline:

```bash
./linux-demo.sh verify
./linux-demo.sh index
./linux-demo.sh start
```

Forward port `8080`. Set `DEMO_DEBUG=true` and open `/?debug=true` for search
evidence; Recording Mode always hides the overlay.
