# Memories AI Codespaces test lab

From `android-app/`:

```bash
./test-lab.sh indexTestMedia
./test-lab.sh enrichTestMedia
./test-lab.sh searchTestMedia -Pquery="red car in snow" -PtopK=10
./test-lab.sh search "dog running on grass"
./test-lab.sh interactive
./test-lab.sh evaluateSearch
./test-lab.sh eval-add
./test-lab.sh eval-list
./test-lab.sh evaluate
./test-lab.sh serveTestMedia -Pport=8080
```

The browser UI is then available on the forwarded port. Put evaluation cases in
`../test-media/evaluation.json`, following `evaluation.example.json`.
The labeling helper validates that expected media exists and writes JSON through
an atomic replacement. Expected filenames are used only after ranking to score
results. Optional `difficulty` and `expected_timestamp` fields can be edited in
the JSON as shown in the example.

The index is stored in `test-media/.memories-test-index` and is wholly separate
from Android/Room production data. On first indexing, the launcher downloads the
pinned, SHA-256-validated TinyCLIP 8-bit ONNX assets. Image pixels and query text
produce shared 512-dimensional embeddings locally. Filenames are display-only.
FFmpeg samples video frames and Tesseract supplies local OCR when installed.

Fast indexing is searchable without waiting for VLM. `enrichTestMedia` runs a
separate bounded progressive worker. The development-only vision service uses Ollama at
`127.0.0.1:11434` with cloud access disabled and `moondream:1.8b` (Apache-2.0).
It writes versioned structured caption sidecars keyed by source mtime, size,
model ID, and analysis schema version. Search reads those indexed fields and
never invokes Ollama. Production Android remains independent of this service.

`test-lab.sh` selects the Codespaces JDK 21 installation because this repository's
Gradle 8.9 wrapper cannot run on JDK 25. Other arguments pass straight through to
the normal Gradle wrapper.
