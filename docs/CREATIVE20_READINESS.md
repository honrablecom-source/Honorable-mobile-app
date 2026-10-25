# Twenty-tool integrated batch

October 24, 2026. Work solo, without computer-use tools or subagents. One integrated feature batch, then one full Android build. All original media, native Seran, account authority and credit policies remain intact.

## Implemented source

Twelve photo effects: negative, solarize, sepia, duotone, gamma, pixelate, soft blur, edge outline, emboss, chromatic offset, halftone dots and scanlines. `PhotoEffects` is validated immutable state; `PhotoEffectsKernel` uses one additional full-size scratch array at most and preserves output-position alpha. `PhotoEffectsControls` and the existing photo inspector expose the tools. Preview and copy export share the renderer.

Photo session version 6 and preset version 3 carry effect state; older versions remain supported. Effects transfer with saved looks while target framing, people, text and local light stay intact. A small allocation improvement in the preceding creative-color kernel is a fix, not a twenty-first feature.

Eight video workflows: equal clip division, project clip-length limit, reverse order, shuffle order, sequence repetition, batch playback speed, caption timing offset and caption line reflow. `VideoBatchTools` produces pure proposals. The activity reviews them against the current project/account snapshot and applies through existing `applyDraft`, preserving soundtrack and one-step undo. Existing limits and draft validation remain authoritative. Existing video draft schemas store the resulting clips without introducing a parallel project format.

## Verification

147 shared JVM cases and 144 RN-native JVM cases pass fresh, including 18 new photo and 14 new video regressions. Three additional Android integration cases cover all photo effects, protected PNG/alpha output and reordered/repeated shared video composition. Both integration suites compile, but have not run on a phone.

TypeScript, 60 React Native tests and lint pass. One final integrated FAST_INTERNAL ARM64 APK passes. Independent verification checks all 797 frozen app inputs, new photo/video tool markers, private activities/provider, drawing/model assets, approved public-debug signature and ZIP alignment. No new dependency or AI model was required.

APK size: 129,055,463 bytes. SHA-256: `409fa2c34747a43dc5ae496e325a5c403bd62b98111bdde572d70c00cb6a87cb`. Exact app source Git tree is in `executable/release.json`. Initial validation correctly rejected an invalid recovery test fixture whose trim range exceeded its selected clip; the fixture was corrected without weakening production validation. Initial evidence is retained separately. The final source freeze and all tests pass.

The illustrated guide adds actual compiled-kernel photo-effect examples and usage instructions for the eight video workflows. HTML UI previews still show the earlier controls; they are not updated native-device evidence. The original example helpers and full validation receipts are retained in the task workspace.

## Remaining limits

Twenty local additions, zero new AI engines. This does not finish the requested 90-feature roadmap. No Android phone is available. Real generation/rigging, hosted accounts/payment operation, iOS native editors, signed phone IPA and store readiness remain unfinished. Reversed clip order is not reverse playback; caption reflow is not speech transcription; pixelation is not secure redaction.
