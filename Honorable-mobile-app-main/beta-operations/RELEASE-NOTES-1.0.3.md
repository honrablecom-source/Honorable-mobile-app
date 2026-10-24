# Honorable 1.0.3 / build 4 — internal candidate

Status: complete ARM64 internal APKs have passed package/signature/alignment checks; exact hashes and scope are in docs/HONORABLE_CURRENT_STATUS.md. Local video drafts now pass a separate complete ARM64 build and five focused draft tests; device storage/runtime behavior is unverified. No physical-device verification or public distribution approval.

- Adds **Read text** to the native photo editor: the bundled on-device ML Kit Latin-script model reads the current edited preview, then shows selectable text for review and explicit copying. No photo or recognized text is sent to the account server or telemetry. Review recognition accuracy before using copied text.
- Pixel-budgeted decoding reduces large exports to fit available heap, capped at 6 million pixels (1.5 million on low-RAM devices) and 8192 pixels on the long edge. Export reports actual output dimensions. Original photos remain untouched.
- Cleans up export/preview bitmaps, handles memory failures, skips stale previews and freezes the edit selected when Save copy opens the document picker.
- Fixes Windows checkout corruption of the tokenizer by preserving exact asset bytes. Model/tokenizer integrity pins are unchanged.
- Adds a self-contained internal variant using release optimization and the public debug signing key. This is a tester artifact, not a store release.
- CI installs the declared SDK/NDK, runs native/JavaScript/server checks, verifies assets/package/signature/ZIP alignment, and records artifact SHA-256/source identity.
- Fixes unused-code lint errors and applies compatible dependency security updates. The production npm audit has no critical advisories; 25 high and 5 moderate advisories remain unresolved.

The working branch includes local Video Editor V1 trim/split/reorder, speed/volume/mute, composition preview and Save As Copy implementation. Its media output still needs physical-device acceptance. Explicit local video draft save/restore is packaged and JVM-tested, using existing encrypted account/source-scoped storage; physical behavior remains unverified. Advanced generative AI image/video manipulation, multitrack editing, store billing and trusted native search completion remain unfinished.

Required phone checks: cold startup without Metro, Google sign-in/session restore, library permissions, real local search, credit completion behavior, editor opening, crop/rotate/undo/compare, Read text review/copy/no-text/error cases, Save copy dimensions, source preservation and low-memory behavior. Test OCR with airplane mode after opening the authorized editor; account authorization still requires the configured HTTPS service.

The existing public Google Web OAuth client ID was recovered privately, and genuine Google sign-in/session restore/logout passed against the local account server. The accepted hosted HTTPS account API URL and Android sign-in remain unavailable. Local build configuration and CI secrets can differ; consult each artifact's configuration booleans. Do not supply client secrets or tokens in chat.
