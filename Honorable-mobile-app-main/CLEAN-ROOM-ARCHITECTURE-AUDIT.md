# Honorable clean-room architecture audit

Date: 2026-08-30

## Scope and clean-room boundary

The current repository is the only implementation source of truth. The untracked
The former `Code engine/` archive was inspected only at the level of subsystem names and
general production-engineering concepts. No source, prompts, strings, tests,
assets, algorithms, or dependencies were copied or adapted from it. The archive
was proprietary, was never part of the application build, and was removed during
the subsequent workspace cleanup.

## Existing architecture before this change

Android is the only production-capable Memories implementation. Compose owns the
UI, `MemoriesViewModel` owns presentation state, `AndroidMediaIndexer` adapts
MediaStore/ML Kit/MediaMetadataRetriever, `LocalMediaDatabase` stores the local
index, and platform-neutral Kotlin implements query parsing and ranking. The
bundled TinyCLIP ONNX model supplies image/text embeddings. iOS is an early
SwiftUI/protocol shell. React Native is a staged presentation migration with a
native Android bridge; it is not the search authority.

```text
Android MediaStore (images + videos)
  -> MediaCapabilityManager (full / partial / unavailable)
  -> discover IDs, dates, modification times, names and durations
  -> IndexJobController lifecycle + process-wide resource budgets
  -> load bounded 384x384 thumbnails
  -> ML Kit OCR + labels; sampled dominant colors
  -> local TinyCLIP image embedding
  -> representative video frames
       -> duplicate-scene suppression
       -> OCR + labels + colors + TinyCLIP per retained frame
  -> SQLite media_index + video_frame tables
  -> immutable in-memory media catalog + multi-vector index
  -> QueryParser (terms, media/date/location/color/activity/OCR constraints)
  -> local TinyCLIP text encoding and short-query prompt variants
  -> vector candidate retrieval
  -> SearchRanker hybrid evidence fusion
  -> confidence decision and evidence explanations
  -> MemorySearchState
  -> Compose results UI / React Native native DTO bridge
```

No Memories runtime requests Internet permission or contains a network client.
The Linux test lab is a development adapter, not the Android production path.

## Current subsystem audit

| Concern | Current state | Main gap or risk |
|---|---|---|
| Indexing | Modification-time incremental scan, validated job lifecycle, resource budgets, per-item stages and fault isolation | Lifecycle is not yet persisted through process death; no durable WorkManager resume |
| OCR/labels | Bundled ML Kit APIs, closed after each request | Failures degrade to empty evidence and need richer internal timing/reason counters |
| Embeddings | Bundled integrity-checked TinyCLIP ONNX; lazy session lifecycle | No device capability/backend policy or memory-pressure callback |
| Video | Five candidate timestamps, perceptual fingerprint selection, timestamped frame evidence | Fixed sampling can miss brief events; decoding each selected candidate twice |
| Database | SQLite schema v5, transactions per media, FK enforcement, bulk frame loading | No corruption recovery policy, schema-level processing-stage state, or batch transaction API |
| Incrementality | URI modification times and stable namespaced media IDs | Modification time is a coarse fingerprint on some providers |
| Retrieval | Cached text embeddings, reusable catalog, multi-vector video lookup | In-memory index is rebuilt after each synchronization and is not persisted as ANN data |
| Ranking | Deterministic hybrid score with detailed breakdown | Raw weighted sum is query-length dependent; weights lack a sufficiently large labeled calibration set |
| Query parsing | Local deterministic filters/concepts and short-query prompt planning | Location grammar and vocabulary remain narrow |
| VLM | Model-neutral cached schema and gated queue; disabled in Android production | No validated bundled Android VLM adapter yet |
| State | ViewModel `StateFlow` separates Compose from engine | Search has no explicit Searching/cancelled state and refresh/search mutual exclusion policy is implicit |
| Permissions | Central `MediaCapabilityManager`, Android 14 selected-library awareness, MediaStore scoped access | Device/UI tests for transitions between partial and full access remain |
| Diagnostics | Score breakdown and non-content timing/count records | No bounded persisted diagnostic ring buffer or on-device developer export |
| Tests | JVM search tests, integration contracts, test lab, security and CI workflows | Device MediaStore, corrupt decoder, process-death resume and database migration tests are missing |
| iOS | Protocol boundaries and privacy shell | Memories discovery, OCR, model, persistence, and search are not implemented |
| React Native | Thin native boundary, JS ranking 0%, reusable native search catalog | Native compilation, device lifecycle, and parity still need verification |

## Conceptual reference study and gap analysis

Only general, widely used engineering principles were retained.

| Reference concept (general only) | Engineering principle | App status/problem | Recommended original mobile design | Benefit | Complexity | Priority |
|---|---|---|---|---|---|---|
| Central lifecycle ownership | Expensive resources need one owner and deterministic cleanup | ONNX previously initialized eagerly | Lazy synchronized Android model runtime, closed by ViewModel | Startup/memory/reliability | Medium | P0 |
| Task orchestration and explicit states | Long work should expose progress, cancellation and isolated failure | Index pass was one loop and one exception could abort it | Typed stages, per-item issues, cancellation propagation, aggregate stats | Reliability/UX | Medium | P0 |
| Bounded caching | Reuse expensive results without unbounded memory | Query vectors were bounded, but media vector index rebuilt per query | Immutable replaceable search catalog plus bounded query cache | Latency/battery | Medium | P0 |
| Structured errors | Separate developer cause from simple user message | Broad catches and silent media failures | Non-content issue category + stage; keep user-facing state simple | Debuggability/privacy | Low | P1 |
| Permission boundaries | Platform access decisions should not leak through UI | Helper exists but capability detail is minimal | Future `MediaCapabilities` adapter including partial access | Maintainability/UX | Medium | P1 |
| Concurrency budgets | Decoding/inference must respect mobile memory | Production loop is sequential, which is safe but not tunable | Future small stage-specific semaphores (decode 1–2, inference 1) after profiling | Performance/safety | Medium | P1 |
| Durable jobs | Background work should survive UI/process lifecycle | Refresh coroutine dies with ViewModel/process | WorkManager unique incremental-index work backed by persisted stage/version state | Resumability | High | P1 |
| Observability | Measure stages without recording private content | Ranking breakdown exists; indexing timing was absent | Aggregate counts/timings only; bounded local developer diagnostics | Tuning/support | Medium | P1 |
| Schema/config validation | Stored artifacts must declare compatibility | Index compatibility exists but is not enforced end-to-end | Persist processor/model/preprocessing versions and selectively reprocess stale signals | Correctness/cost | High | P1 |
| Extensible service registries | Swappable subsystems aid tests | Interfaces already cover embeddings/OCR/video | Prefer constructor injection; avoid a global registry/service locator | Testability | Low | P2 |
| Feature flags | Risky paths should be independently reversible | RN migration and VLM already gated | Keep compile/runtime flags narrow and typed | Rollback safety | Low | P2 |

## Concepts intentionally rejected

- Remote agent loops, cloud model APIs, authentication flows, MCP/tool execution,
  shell commands, code-edit tools, and conversation context are unrelated to
  private media retrieval and would expand privacy and attack surfaces.
- A global service locator was rejected in favor of explicit constructor-owned
  dependencies.
- General parallel-agent/task fan-out was rejected for media processing until
  real device memory and thermal profiling establishes safe stage limits.
- Network retry/backoff patterns were not applied to local media. Local retry
  must be narrow and based on provider/decoder error classification.
- Reference UI, prompts, telemetry, updater, bridge, and session persistence
  concepts were not adopted.
- A wholesale architecture rewrite was rejected. Existing query/ranking/model
  contracts remain the product source of truth.

## Improvements implemented in this change

1. Namespaced image/video MediaStore IDs prevent primary-key collisions and
   automatically migrate an unchanged URI when its old ID is detected.
2. Indexing now reports typed stages, failed/skipped counts, aggregate duration,
   and non-content issue categories. Cancellation is never swallowed, and one
   bad media item no longer aborts the library.
3. `LocalSearchCoordinator` owns a reusable immutable catalog and vector index.
   Search no longer reloads SQLite or rebuilds vectors for every query.
4. The local vector index retains every representative video view and uses the
   strongest vector for that media item.
5. Short visual queries receive deterministic local CLIP prompt variants for
   candidate recall; raw-query semantics remain ranking authority.
6. TinyCLIP model assets/session load on first inference, use one synchronized
   runtime, and close deterministically.
7. SQLite foreign keys are enabled. Video frames are loaded once for the full
   catalog instead of one query per media row.
8. Privacy contracts now scan all search runtime sources and assert that the
   application does not request Internet permission.
9. Thumbnails and decoded video frames are explicitly recycled after local
   evidence extraction, bounding native bitmap pressure during long scans.
10. Each indexing pass owns a validated, privacy-safe job lifecycle with
    terminal partial-failure, failed, and cancelled outcomes.
11. A process-wide resource scheduler limits decoding, OCR, local vision,
    TinyCLIP inference, video decoding, and SQLite writes by cost category.
12. `MediaCapabilityManager` centralizes Android permissions, recognizes Android
    14 selected-library access, and prevents unauthorized media-type scans.
13. `EngineDoctor` provides on-demand, local-only capability, TinyCLIP lifecycle,
    SQLite integrity/schema/count, and active-job diagnostics without filenames,
    queries, OCR, captions, or other private media content.

## Remaining prioritized work

- **P0:** Run Android CI/device tests with SDK 35; validate the schema-v5 ID
  migration against a copied real index before release.
- **P1:** Add WorkManager unique incremental indexing with persisted processor
  states, pause/cancel semantics, and charging/thermal-aware policy.
- **P1:** Add device tests for Android 14 selected-library/full-library transitions.
- **P1:** Cache decoded selected video candidates so frames are not decoded twice.
- **P1:** Persist model/preprocessing versions and selectively recompute stale
  embeddings without discarding OCR/metadata.
- **P1:** Build a privacy-safe local diagnostic ring buffer and benchmark index,
  search, database, OCR, inference, memory, and thermal behavior on low/mid/high
  devices.
- **P1:** Expand labeled media evaluation before changing ranking weights or
  confidence thresholds.
- **P2:** Normalize ranking signals only after calibration data exists.
- **P2:** Add React Native bridge ordering/confidence parity and device lifecycle tests.
- **P2:** Implement the iOS Memories stack using Photos, Vision, AVFoundation,
  local persistence, and a validated bundled model.
- **P3:** Add an optional on-device VLM only after size, latency, memory, license,
  and quality gates pass.
