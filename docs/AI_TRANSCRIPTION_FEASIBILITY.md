# Local speech transcription feasibility

October 23, 2026. **Assessment only: 0 new features; no transcription executed.**

Verified locally using package metadata, file names and hardware queries; no model imports/downloads:

- Python 3.12.10, FFmpeg 9.0.2, CPU-only Torch 2.14.1+cpu. Whisper, faster-whisper, CTranslate2, PyAV, transformers and ONNX Runtime packages are absent from that Python installation. No Whisper runtime/weights found in the scoped repository/`.tools` scan.
- NVIDIA RTX 2000 Ada: 15,356 MiB VRAM, 12,894 MiB free; GPU inference libraries unverified. Windows exposes 4 CPU cores/8 threads, 16 GiB RAM (~4 GiB free) and ~37 GiB disk free. These are momentary readings.
- Android NDK 27.1.12297006 and CMake 3.22.1 are available; no phone exists for acceptance.

The smallest proposed proof is a **separate desktop CPU worker**, using a pinned whisper.cpp binary and `tiny.en`, after runtime/model acquisition is authorized. Upstream lists tiny at 75 MiB disk/~273 MB memory; actual process footprint and speed remain unmeasured. [Runtime/model footprint](https://github.com/ggml-org/whisper.cpp#memory-usage). Runtime and original weights use MIT licenses; retain notices and verify downloaded artifact provenance. [Runtime license](https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE), [weights license](https://github.com/openai/whisper#license).

First transcribe a synthetic/consented 15–30-second fixture with a reference transcript. Existing FFmpeg can produce 16 kHz mono PCM. Measure text/timestamp errors, silence hallucinations, elapsed time, peak memory and cancellation; write bounded SRT for the existing importer. This proves a desktop engine only.

Android remains a separate JNI/audio-decoding/model-delivery project; upstream supplies an [Android example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android). Require private temporary audio, cancellation/cleanup, source-relative timestamps, reviewed atomic caption application, stale-result rejection and existing cue/recovery limits. Preserve guest AI restrictions/account authority. No media upload; no Android performance, quality or lifecycle claim without device tests.
