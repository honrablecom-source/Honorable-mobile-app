# C195 — Experimental on-device AI upscale copy

**1 new experimental user-facing source workflow;0 separate completed fixes/scaffolding. Source181/500;319remaining.** Source/API/tests accepted. No current full-app/APK, Android inference, physical-device or store acceptance.

## User outcome and bounds

Photo editor → On-device AI → AI upscale copy creates a separately reviewed neural reconstruction. The complete edited preview is explicitly fitted within1024pixels per edge before inference; nothing is cropped. Swin2SR doubles that working grid to≤2048pixels per edge. Setup explicitly says this is **not a2× enlargement of the full-resolution original**. Transparency is white-matted. Original media/current edit/history stay unchanged; no Apply/Undo mutation is necessary for an exported copy.

The bundled model runs locally, without uploading photos or queries. Model notice/full license is accessible before inference. Stop terminates active native inference; native model construction may settle after Stop, and the parent's busy lease remains held until worker settlement. No stale background/source/generation/account/mode result may publish or share. A watchdog terminates active inference after owner invalidation or15minutes. This is an attempt limit, not phone performance evidence; large images may take minutes, exceed device memory or fail.

The final review displays the **decoded exact PNG** at full dimensions, with horizontal/vertical pan and1×/2×/4× zoom. Confirmation starts unchecked. One Save/share action verifies exact byte length/SHA again immediately before the existing FileProvider handoff. The receiving app can retain the copy. PNG contains rendered RGB, without original EXIF, filename/URI, layers or editor history. Private own-prefix cache≤16MB/PNG,≤4copies/64MB,24h expiry; other features' files are untouched. Shared cache files remain readable until expiry; URI is not one-use.

AI estimates detail and can change faces, lettering and patterns; it does not guarantee recovery of original information or improvement. Parent uses existing Android filtered fitting, which differs from the antialiased host quality fixture. Arbitrary camera/fitted-preview quality remains unverified.

## Actual model and pipeline

- Unmodified `Xenova/swin2SR-lightweight-x2-64`, revision `92a21aca5713f20faf9a87590cdfbdce2e34112c`, upstream author `caidas/swin2SR-lightweight-x2-64` revision `07e8e61492ce6daf4f34c651622960faf110c17f`.
- 8078888bytes; SHA256 `c2abbfe0cc8e685b5e11964970f8ebe3d24072e904fd5545a30ac31ec1e110db`. Author card declares Apache2. Current assets include full license, authors/converter attribution, immutable revisions, configs and provenance. No weights changed/trained.
- Existing ONNX Runtime1.20; dedicated session/per-run RunOptions. Shared search environment is never modified/closed. Native Stop and options-detach serialize under one lock; session closes only after inference settles. Exact size/SHA required before session creation.
- RGB float NCHW /255; official edge-inclusive symmetric padding to next multiple8, including extra8 when divisible. Output NCHW RGB2×. No interpolation output substitute. Source owns matted pixels/fingerprint; output validates every float, including unused context, and rejects incomplete/nonfinite output. Finite values clamp/round only at RGB conversion.
- Global8-aligned tiles: core128/halo128/max384edge; single whole inference when padded input fits. All original working pixels receive exactly one output core. Conservative architectural interval bound103pixels from24alternating-window attention layers plus group/body/input/upsampler3×3convolutions supports halo128. Finite−100attention masks leave mathematically nonzero wrapped paths: this derivation and native synthetic comparison do not establish all-platform bit identity.

Production: `PhotoNeuralUpscale.kt`, `PhotoNeuralModel.kt`, `PhotoNeuralUpscaleControls.kt`, narrow `PhotoEditorActivity.kt` integration; three focused test files. Assets under current standalone `assets/neural-upscale/`; existing RN source set shares them. No C195 Gradle/dependency/manifest/search/ranking/credit/catalog/server-ledger edits. No personal media, Seran holdout or expensive search benchmark used. Old frozen138 snapshot has no new model folder.

## Verification

**31current cases PASS56.259s** after actual whole current PhotoEditorActivity+3helpers API37/real AndroidORT/SDK compilation.15pure/pin cases cover dimension overflow, immutable white matte/fingerprint, normalized RGB/symmetric padding,121dimension-pair exact tile coverage/bounds/context alignment, non-square/thin all-pixel assembly, all-output finite/shape rejection, clamping, cancellation/errors/no fallback, model-size/SHA/stream-close failure.16SDK34native-graphics UI cases cover exact full decoded PNG pixels/ImageIO, unchecked one-share, Stop and deferred busy release, copied-bitmap settlement, stale owner/pause/publication, mutation/receiver failure, zoom, cache isolation/budget and11actual Root admission states. UI inference is explicitly injected synthetic output.

**26unchanged regressions PASS83.028s** on the exact new Root:9C170subject asset controls/GoogleTask/admission and17C193OCR reader/privacy controls/Root. No assertions/deadlines weakened. Source compile has existing deprecation warnings; SDK34Robo is not API37runtime or a phone.

**Real native model evidence:** same exact Android-compiled production core/adapter classes ran bundled weights through host ORT1.20. All262144public-fixture RGB pixels exactly match independent Java inference. Stop observed an actual JNI run then settled12ms without output; global ORT environment remains usable after private session close. This latency is host evidence only. Core/model class bytes in the whole-Root test jar match the real-model-tested jar exactly. Earlier synthetic208×184 whole-vs12tiles compares459264raw outputs exactly (max0); not a broad benchmark/general tile-quality proof.

Exact current Root equals accepted C193baseline outside1field/1method/2entries/2lifecycle-close calls. Other7C193files and271pinned RN drawing assets unchanged; both existing FileProviders verified. Current/frozen assets independently isolated; model absent old snapshot. Evidence: `.context/noble-fast-ai/C195/acceptance.json`, UI/source/probe/regression/scope receipts and retained full logs.

Current Root test jar SHA256 `56e192a343e8f29e04c8e39b66cd13cee915eb3ff69ea3e2d8ddfbe141027f64`. Seven source/test aggregate SHA256 `78b0f61497d05b5d50e4e7d73c187793a3055f6e6c8152d88fea093b47f9fab3`.

## Retained adverse probes and quality limits

Initial compact ESPCN x3 ran but showed visible ringing/checkerboard and poor reconstruction on Java2D-downsampled public astronaut. Its upstream fixed224graph was separately converted by preserving all8initializer blobs/Conv+Relu nodes and replacing verified one-channel fixed shuffle with DepthToSpace. Original-vs-converted maximum raw difference7.15255737305e-7; non-square/full-vs-tile passes. It is **not bundled**. Corrected antialiased x3 fixture improves only0.259900dB over same-factor cubic and remains visibly inferior to the selected model. All conversion/rejection evidence remains; no tuning or cherry-picked deletion.

Initial Swin photo used wrong edge-exclusive reflection; correcting only symmetric padding gave RGB PSNR27.963063 vs Java2D bicubic30.771891. The low-resolution fixture itself lacked antialiasing. An independently implemented cubic antialiased fixture matches the documented KAIR/Swin convention (half-pixel centers, expanded downsampling support, symmetric edges). Exact halfscale8tap rational weights, constant preservation and Nyquist cancellation pass. Same unmodified Swin weights with this fixture produce RGB PSNR35.265787 vs cubic30.224499, +5.041289; visual artifacts are reduced. This is **one** public photo, not general quality acceptance; absolute x2/x3 scores are not comparable. Earlier negative cases stay recorded.

Public NASA/scikit-image astronaut fixture SHA256 `88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5`; no owner media. Public/local image inspection only, no computer-use tools. All probe handles terminal:86607,65798,17447,16390,20682,84492; current tests76018 and regressions79705 terminal0.

## Remaining validation

Real Android/phone inference, arbitrary camera/fitted-preview quality, memory/thermal/latency, physical Stop/pan/accessibility/lifecycle, process death, actual external receiver/save, current integrated app/APK and store readiness unverified. Current source excluded frozen138 packaging; next broad build checkpoint~238. Goal500 and genuine-AI priority continue.

## Primary sources

- [Author model/license](https://huggingface.co/caidas/swin2SR-lightweight-x2-64)
- [ONNX conversion](https://huggingface.co/Xenova/swin2SR-lightweight-x2-64)
- [Official preprocessing](https://github.com/huggingface/transformers/blob/v4.36.0/src/transformers/models/swin2sr/image_processing_swin2sr.py)
- [Official model implementation](https://github.com/huggingface/transformers/blob/v4.36.0/src/transformers/models/swin2sr/modeling_swin2sr.py)
- [Original inference/evaluation](https://github.com/mv-lab/swin2sr/blob/main/main_test_swin2sr.py)
- [KAIR cubic resizing](https://github.com/cszn/KAIR/blob/master/utils/utils_image.py)
- [Earlier ONNX ESPCN](https://huggingface.co/onnxmodelzoo/super-resolution-10)
- [DepthToSpace](https://onnx.ai/onnx/operators/onnx__DepthToSpace.html)
- [Public astronaut documentation](https://scikit-image.org/docs/stable/api/skimage.data.html#skimage.data.astronaut)
