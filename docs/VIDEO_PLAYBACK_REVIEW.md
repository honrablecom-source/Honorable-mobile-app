# Clip playback approval

0 new user-facing features;1 shared correctness fix;0 scaffolding. Source101/500 and batch63 remain unchanged. Focused host/API34 validation passes; actual native picker/render/audio/device and full APK acceptance remain unverified.

Clip speed and source-volume pickers previously used untracked dialogs and partial project/session checks. They omitted full source/duration/generation and soundtrack snapshots. Mutation and preview were also in one failure path.

Both now capture the existing full foreground project/source/duration/range/private-session/generation review before widgets. Their single-choice callbacks require the exact visible owned inspector before reading the chosen value. They use the shared inspector commit boundary and original project `setSpeed`/`setVolume` methods. A foreign/hidden/stale inspector cannot apply or consume a successor. Successful application consumes approval; Undo cannot revive the old callback. Matching current values consume approval without calling the model or adding history. Invalid model values remain refused through the existing validation boundary.

The speed choices0.5×/1×/1.5×/2×, source-volume choices100%/50%/0%, and the appended Keep current volume label/value are byte-identical. Custom values retain their precision. Source volume changes only the selected clip's original audio; the separate soundtrack keeps its settings. Speed changes duration through the original timing model, with source captions following that pipeline. Words, source metadata and other appearance remain unchanged. Successful application and preview failure have separate messages. The project/codec/caption/timing implementations remain byte-identical.

## Validation and limits

549 focused host tests pass:8 new plus541 retained. New cases execute actual Activity approval/project state for speed-only/volume-only changes and Undo, custom volume, no-op history, stale foreground/source/duration/range/generation/session/recovery/busy/music refusal, old/hidden/untracked owner isolation, invalid values, codec precision for every speed, and paused/busy/recovery entry before widgets. Current Activity and tests compile against pinned API34 references and the hash-verified retained graph; only existing deprecated Android API warnings occur.

18 source checks verify capture/choice ordering, tracked inspector ownership, shared commit, unchanged choices/custom label, unchanged model/caption/timing/codec, no-op history and separate preview reporting. Evidence: `.context/noble-fast-playback-review/verification.json`, `tests.log`, `compile.log`, `wiring.json`, `exit.json`.

Fixtures allocate constructor-free dialog/view/Activity metadata and execute the real approval and project functions. They do not construct actual picker widgets or exercise native selection/dismiss/accessibility, physical lifecycle, preview/rendered/exported video or audio, provider access or device behavior. Those remain integration/device checks. No configured SDK37/full module/Gradle/native/APK build or readiness claim. No dependencies, uploads, benchmark, ranking or ledger changes; broad build remains approximately138 source outcomes. Shared notes were left untouched until noble-main explicitly released its279d246 photo-disclosure publication.
