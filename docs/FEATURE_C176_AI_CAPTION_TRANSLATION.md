# C176 — Local AI caption translation (unfinished)

**0 new completed features;0 completed fixes/scaffolding;1 unfinished AI workflow.** Current register181/500 after peerC195 neural upscaling; C176 does not change it. Goal500 and next broad native checkpoint~238 remain active.

Intended outcome stays complete: actual local translation of the selected clip's existing caption track → correct every generated text → full source/timing/change review → one source-owned Apply/Undo preserving timing/other edits/music and clearing obsolete current subtitle-file binding. Existing manually translated worksheet and bilingual pastedSRT tools are not counted again. Translation languages/presets/buttons are not extra features. Start with explicitly English→Spanish model support, no claim of language detection or unsupported translation directions.

## Verified decision and artifacts

No native Translator/model source existed. The [ML Kit Android SDK](https://developers.google.com/ml-kit/language/translation/android) offers17.0.3 but its [usage terms](https://developers.google.com/ml-kit/language/translation/translation-terms) adopt [attribution beside results](https://docs.cloud.google.com/translate/attribution), complicating ordinary editable subtitle preview/export. No Google translation SDK/dependency was added and no API text was sent. We are implementing an openly licensed local-model path rather than restricting the normal edit/export outcome.

[Helsinki Opus English→Spanish](https://huggingface.co/Helsinki-NLP/opus-mt-en-es), upstream revision5bc4493d463cf000c1f0b50f8d56886a392ed4ab, declares Apache2. [Xenova ONNX conversion](https://huggingface.co/Xenova/opus-mt-en-es), revision4b002a4c7edd54a7ced58877258b87f7efd3f892. Downloaded public artifacts verified from repositoryLFS metadata:

- encoder_model_quantized.onnx52,899,742B SHA03434edd1147e6c49e22066e9dc5888235e99cc35a6711d1c4ea5b06455d5da9.
- decoder_model_merged_quantized.onnx60,212,804B SHA58ee5ddd6e22d1693d8722b90c1486afb93700a4376cf28fd74175052ad530ae.
- Original upstreamsource.spm801636B SHA4dd547c24816a335e7b0b2e63376a8f1b3cbfc671eda5ab808dd44fdadaa8791; jointvocab.json1590040B SHA257f346d7a6b2ecceafcca8ba05648ce2fd68dfaf105fb0e913dca7198f3f6d5.

Files are tool-only under /tmp/noble-translation-probe, not bundled in app assets or installed on a user phone. ExistingORT1.20 unchanged. Same global environment remains open; model sessions are separate. No search preprocessing/ranking/Seran holdout/benchmark/credits/catalog/ledger/media upload touched. Public metadata and original logs live .context/noble-main-c176.

Actual existingORT1.20 host sessions loaded both pinned models. Encoder accepts int64 IDs/mask and returns FLOAT[batch,source,512]. Merged decoder has6layers of8heads/64width past encoder+decoderKVs and boolean cache branch. Original configured generation is4beams; greedy inference is only a protocol smoke, not the final production algorithm. Generated input “Hello, how are you?” produced complete “Hola, ¿cómo estás?” with EOS0 and IDs[2119,2,50,6676,1221,21,0]. Cache-disabled/full-prefix and cache-enabled/native past reuse produced identical IDs; cached host observation4.94s is not phone performance. First encoderKV outputs must remain alive through cached runs; cached branch encoder placeholders must not replace them.

## Actual tokenizer source component

Converted tokenizer.json uses Precompilednull, which panics officialRust tokenizers0.22.2, and marks unk_id2 (the comma). Transformers.js3.8.1 itself warns that fast Marian tokenization can be inaccurate and normalizes with its own nmt_nfkc approximation; do not pass it as identity normalization. Neither converted tokenizer nor a silently patched copy is accepted as a reference.

New VideoMarianTokenizer reads original sourceSentencePiece protobuf, exact binary nmt_nfkc/Darts map and original32k source-piece scores, performs bounded UnigramViterbi and maps pieces into joint model IDs. It does not add JNI or alter the search tokenizer. Protocols were checked against [SentencePiece0.2.1 normalizer](https://github.com/google/sentencepiece/blob/v0.2.1/src/normalizer.cc)/protobuf and Apache2/BSD Darts data format. Full appropriate notices still need to ship with the completed model setup.

**Verified component:** original official SentencePiece0.2.1 normalization and IDs match21generated Unicode cases, including ASCII, punctuation, full-width, ligatures, composition, NBSP/zero-width, emoji, CJK, Arabic compatibility forms and long input. Blank inputs normalized only and correctly refused by production encode.6portable JVM tests PASS0.386s cover best path/EOS, unknown surface fusion/joint mapping, invalid markers/bounds, malformed protobuf/vocab, defensive ownership and cancellation. Initial parity correctly caught café/dejà-vu accented unknown-surface mapping: Marian maps a source-unknown surface into joint vocabulary when possible, not blindly toUNK1. Fixed that protocol and all21thenpass. Failed original log remains. Source/test SHA and receipts tokenizer-milestone.json. This is not a completed user workflow or representative translation accuracy evaluation.

## Required next work

Implement configured4beam decoding with proper cache batch/reorder/EOS scoring, bounded inputs/outputs and native Stop/session/tensor ownership; verify actual model on generated fixtures and independent token/reference checks. Add production adapter, exact pinned optional-artifact model cache with unchecked public-download consent, complete licenses/notices, source-owned caption correction/full original-vs-new review/one Apply/Undo and failure/no-partial-change behavior. Preserve original task scope rather than delivering a smaller read-only translation demo or branded-export restriction. Test actual API/parent/UI/dataflow. Then count exactly1experimental source workflow, never languages or buttons.

No current app/Gradle/native/APK build, actual device/model download/phone inference/latency/memory/quality/store claim. No Seran holdout or expensive benchmark. Only generated test text/public weights used; no private captions sent remotely. Current source excluded from immutable oldfrozen138 packaging.

## Current production-component checkpoint (unfinished)

VideoMarianBeam/Model/Store/TranslationPlan source now exists: four-beam scoring/lengthpenalty1/defaultstopping/EOS, batchdecoder-cache reorder and retained encoderKV, sticky nativeRunOptionsStop, dedicated sessions/pinned4artifacts, consent-only public model fetch with HTTPS/size/SHA/atomic per-file repair, and all-caption count/timing-locked draft/correction validation.18focused JVM cases PASS0.461s on current source. Initialcompile correctly caught an ORT buffer API misuse(getFloatBuffer returns FloatBuffer, not Optional), fixed before this gate. Source generation now follows the model's Transformers4.34 default early_stopping=false heuristic, not the earlier conservative never-style bound. No forced EOS is synthesized; only completed hypotheses are eligible.

This checkpoint is still0complete workflows. Actual production4beam/cached-inference/Stop/resource/model-transport and Android API validation remain, followed by model license/setup controls and complete all-caption correction/pairedreview/ownedApplyUndo. No C176Activity/UI/Gradle/dependency changes yet. Earlier Java greedy probes are protocol evidence, not acceptance of this new4beam production code. Owner explicitly authorized pushing all changes; frozen unfinished source is documented accordingly and never counted.

## Native UI checkpoint — implemented, verification pending

The actual production four-beam decoder/model-store/tokenizer completed all three generated host fixtures: Hello→Hola, Welcome to Noble→Bienvenido a Noble, and the original video stays on your device→El vídeo original permanece en tu dispositivo. Dedicated sessions closed normally; saved models required0connections. Host observation12.773s for all three is not phone/quality acceptance.

Native VideoTranslationControls, its Activity entry/owner/lifecycle guards, correction/review and source/target license notice are now implemented. Source-English confirmation and public-model download consent start unchecked. All stored selected-clip captions translate, including cues outside current trim; exact source times/count are locked. Expanded output remains fully visible for correction before existing240character/three-line limits. Final review displays every original and Spanish result, requires unchecked confirmation and applies once with Undo/oldbinding cleanup. The prototype is still0count; native UI/parent/wholeAPI cases are pending and real phone/download/quality/Stop/resource acceptance remains open. No claim that this UI is ready follows from the earlier18component cases.

Owner repeated the request to push all updates, so current5UI/Activity/test/notice changes and this truthful status are frozen for a normal [skip ci] WIPcheckpoint. Actual current Activity/API37+28cases are running in .context/noble-main-c176/ui-compile-0.log/ui-jvm-0.log (session14319; compiler374794 observed live). Do not restart solely because observation expires. After terminal results, fix genuine failures and push the next verified update; count C176 only after the full intended workflow gates pass.
