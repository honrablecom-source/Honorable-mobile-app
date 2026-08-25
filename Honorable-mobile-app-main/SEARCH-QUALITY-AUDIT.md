# Honorable search-quality audit

Baseline date: 2026-08-25. Android remains the source of truth. This document records the implementation before any ranking-weight change. No weight, confidence threshold, TinyCLIP asset, tokenizer, preprocessing, OCR engine, or video aggregation constant was changed in this execution because the repository contains zero real `test-media/` fixtures and therefore cannot produce an acceptance-quality baseline.

## Pipeline

1. `QueryParser` normalizes text, extracts media/subtype/time/location hard filters and structured colors, activities, scenes, objects, people, negatives, OCR phrases, and at most six semantic concepts.
2. `SemanticQueryEncoder` caches TinyCLIP text vectors for the raw query and concepts.
3. `LocalVectorIndex` uses multi-probe SimHash buckets and exact cosine reranking to return up to 500 candidate IDs. It falls back to all records when no semantic candidates exist.
4. `SearchRanker` applies hard filters, then deterministically combines full-query cosine, concept coverage, OCR, labels, metadata/time/type, color, video frames, cached VLM evidence, and negative penalties. Filename contributes 0%.
5. Video frame scoring suppresses adjacent frames with identical fingerprints inside 1.5 seconds, scores per-frame lexical/VLM/cosine/concept coverage, and aggregates the best three at factors 1.0/0.5/0.25. The winning indexed-frame timestamp is returned; no finer precision is invented.
6. `confidenceDecision` requires Top1 semantic cosine >= 0.30 and semantic Top1–Top2 margin >= 0.03. No candidate is confident. `MemoriesViewModel` returns an empty result list when confidence fails.
7. Cached local VLM fields can rerank. Gated refinement is disabled by default and bounded to at most 3 candidates when enabled. No cloud media service is used.

## Current signals

| Signal | Current weight/threshold | Purpose | Known strength | Known failure mode | Android/Linux shared |
|---|---:|---|---|---|---|
| Full TinyCLIP semantic | 2.5 | Broad visual-language similarity | Handles natural visual descriptions without exact labels | Weak on text details, fine-grained identity, and near ties | Yes: shared ranker; byte-identical model/preprocessing adapters |
| Concept coverage | 3.5; similarity gate 0.20 | Reward multiple requested concepts | Prevents one strong fragment from dominating a compound query | The weakest covered concept and small concept sets can be unstable | Yes |
| OCR token | 2.4 per hit | Match indexed visible text | Useful for receipts, confirmations, screenshots | Incidental/random text can add score when query is not OCR-intended | Yes normalization/ranking; OCR adapter differs |
| OCR exact phrase | 5.0 per phrase | Strong quoted/screenshot text evidence | Precise for genuine phrases | Proper-name extraction is narrow; screenshot chunking is coarse | Yes |
| Metadata/time/type | 1.3 per match | Support indexed metadata and explicit filters | Deterministic; hard dates/types prevent invalid candidates | Generic metadata terms can add weak lexical noise | Yes |
| Image labels | 1.8 per matched term | Structured object/scene evidence | Robust when ML Kit/Tesseract-side labels are correct | Label vocab is coarse and adapter outputs differ | Yes scoring; evidence producer differs |
| Color | 2.0 per match | Support explicit color intent | Auditable and local | Global dominant color may be background rather than queried object; no shade model | Yes |
| Video frame | 2.4 after 1/0.5/0.25 aggregation | Rank representative moments and return timestamp | Preserves real indexed timestamps and multiple supporting frames | Five Android sampling positions can miss short events; video-level ID insertion can collapse frame vectors | Yes selector/ranker; decoder differs |
| Negative evidence | -3.0 per hit | Penalize explicitly excluded concepts | Prevents obvious contradiction | Only indexed label/metadata negatives are seen | Yes |
| VLM caption | coverage ×1.10; +0.85 full-coverage bonus | Match cached description | Useful for structured multi-signal descriptions | Lexical caption overlap is not semantic and depends on cache quality | Yes |
| VLM objects | 1.35 per hit | Structured object evidence | Auditable | Vocabulary/model coverage limits | Yes |
| VLM activities | 1.15 per hit | Structured action evidence | Helps action queries | Still-frame action ambiguity | Yes |
| VLM scene/environment/color | 0.90 scene; 0.70 color | Contextual support | Complements TinyCLIP | Context/background may dominate | Yes |
| Match display confidence | strong >=7.0; possible >=2.5 | Per-result UI label | Simple and deterministic | Total score is query-length dependent and is separate from result-set confidence | Yes |
| Result-set confidence | semantic >=0.30; semantic margin >=0.03 | Honest no-match gate | Rejects low-cosine and ambiguous Top1 | Ignores cross-signal agreement and query specificity | Yes |

## Query and evidence gaps

- Existing object vocabulary omits common retrieval terms such as `racket`, `net`, `court`, `sand`, `receipt`, and clothing beyond shirt/dress/shoes.
- “tennis” is not an activity alias, so `blue shirt at tennis` loses explicit activity structure.
- Multi-word shades such as dark/light blue are not represented.
- OCR intent is inferred primarily from quotes, proper-case phrases, or screenshot subtype; document words such as confirmation/receipt/booking are not an explicit intent flag.
- Location extraction can mistake visual context after “at” for geographic location and then hard-filter every candidate.
- Confidence does not currently consider agreement between OCR/color/labels/VLM or query specificity.
- Near-duplicate diversification is absent; results are strictly score ordered.

These are audit findings, not claims of improvement. Parser/ranker changes must wait for a consented labeled library large enough to measure Top1, Top3/5, MRR, false positives, no-match correctness, latency, and candidate count. The reusable evaluator already reports recall/MRR/latency and timestamp accuracy; confidence/no-match and candidate-count metrics remain future evaluator work once real labels exist.

## Baseline result

`test-media/` has 0 media and 0 valid evaluation cases. Consequently Top1, Top3, false-positive, no-match, latency, and candidate-count baselines are **NOT MEASURABLE**. `android-app/search-evaluation/evaluation-manifest.json` records the requested cases as blocked rather than inventing expected files. This data blocker prevents responsible ranking optimization and before/after claims.
