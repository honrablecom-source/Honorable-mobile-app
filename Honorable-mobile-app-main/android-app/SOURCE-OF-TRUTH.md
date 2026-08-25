# Honorable Android source-of-truth map

The Android application is the product and visual source of truth. The Linux/Codespaces demo is a platform adapter and browser presentation of these same contracts.

| Concern | Android source of truth | Linux adapter |
|---|---|---|
| Root navigation and screens | `app/src/main/java/app/honorable/MainActivity.kt` (`HonorableShell`, `MainTab`) | `test-lab/web/phone.js`; rendered inside the device shell |
| Visual tokens | `MainActivity.kt` (`Brand*`, `DarkColors`, `AppType`, `AppShapes`, glass components) | `test-lab/web/phone.css` Android-parity components |
| Memories flow | `MemorySearchState`, `MemoriesScreen`, `SearchStage` | `/api/status`, Android-style landing/searching/results states |
| Query parsing and refinement | `search/SearchCore.kt` (`QueryParser`, `QueryRefiner`) | Same Kotlin source compiled into `:test-lab` |
| Hybrid ranking and explanations | `SearchCore.kt` (`SearchRanker`) and `SearchPipeline.kt` (`HybridSearchEngine`) | Same Kotlin source compiled into `:test-lab`; no frontend ranking |
| Confidence and VLM gating | `SearchCore.kt`, `VisionEnrichment.kt` | Same Kotlin source and schema compiled into `:test-lab` |
| TinyCLIP | `AndroidTinyClipEmbeddingService.kt`; bundled ONNX/tokenizer assets | ONNX Runtime bridge using byte-identical assets and the same CLIP normalization constants |
| OCR | ML Kit (`AndroidMediaIndexer`) | Local Tesseract adapter; normalized/scored by shared `OcrNormalizer`/`SearchRanker` |
| Media source | Android `MediaStore` in `AndroidMediaIndexer` | `DirectoryMediaSource` rooted only at `test-media/` |
| Index storage | `LocalMediaDatabase`, `MediaRecord`, `VideoFrame`, `VisionUnderstanding` | Linux persistence of the same platform-neutral records |
| Video | `RepresentativeFrameSelector` plus Android media decoder | Same selector/ranker plus FFmpeg decoder |
| VLM | Shared `VisionUnderstandingService` and `VisionUnderstanding` | Ollama/Moondream adapter populating the shared fields/cache |

Platform differences remain confined to media discovery/decoding, OCR implementation, persistence, and presentation. Filenames are identity/display fields only and never ranking evidence.
