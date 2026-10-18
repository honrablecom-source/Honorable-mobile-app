# Honorable 1.0.3 / build 4 — internal candidate

Status: native compilation and automated checks passed; full APK packaging is in progress. No physical-device verification or public distribution approval.

- Adds **Read text** to the native photo editor: the bundled on-device ML Kit Latin-script model reads the current edited preview, then shows selectable text for review and explicit copying. No photo or recognized text is sent to the account server or telemetry. Review recognition accuracy before using copied text.
- Pixel-budgeted decoding reduces large exports to fit available heap, capped at 6 million pixels (1.5 million on low-RAM devices) and 8192 pixels on the long edge. Export reports actual output dimensions. Original photos remain untouched.
- Cleans up export/preview bitmaps, handles memory failures, skips stale previews and freezes the edit selected when Save copy opens the document picker.
- Fixes Windows checkout corruption of the tokenizer by preserving exact asset bytes. Model/tokenizer integrity pins are unchanged.
- Adds a self-contained internal variant using release optimization and the public debug signing key. This is a tester artifact, not a store release.
- CI installs the declared SDK/NDK, runs native/JavaScript/server checks, verifies assets/package/signature/ZIP alignment, and records artifact SHA-256/source identity.
- Fixes unused-code lint errors and applies compatible dependency security updates. The production npm audit has no critical advisories; 25 high and 5 moderate advisories remain unresolved.

Video Editor V1 described in the supplied handoff is absent from both public branches. Advanced AI image manipulation, video editing/export, store billing and trusted native search completion remain unfinished.

Required phone checks: cold startup without Metro, Google sign-in/session restore, library permissions, real local search, credit completion behavior, editor opening, crop/rotate/undo/compare, Read text review/copy/no-text/error cases, Save copy dimensions, source preservation and low-memory behavior. Test OCR with airplane mode after opening the authorized editor; account authorization still requires the configured HTTPS service.

The public Google Web OAuth client ID, HTTPS account API URL and authorized beta deployment are not configured on this machine. This build cannot validate sign-in without them. Do not supply client secrets or tokens in chat.
