# Build cadence — owner instruction, November4

Owner: “dont build after every fetue make sure you make a lot of fetures befor you build lieke for example100”.

Implement roughly100 additional distinct user-facing outcomes between full native/APK build batches. The working baseline when this instruction arrived is38 source outcomes in CREATIVE_500_REGISTER.json; the next broad build target is approximately138 source outcomes. This is a planning cadence, not a requirement to invent filler, recount existing capabilities or declare unfinished work complete. Owner can explicitly change the target or request a build sooner.

During implementation, run focused model/unit/UI tests, TypeScript and lint as appropriate. Use existing live previews and lightweight review checks. Do not run whole-app Gradle/native compilation, integrated test builds, release/shrinker builds or APK packaging after each feature. Earlier extensive native validation is complete; do not repeat it for individual additions.

Record each actual source outcome in the existing register; fixes, usability refinements, tests, scaffolding, platform copies and presets are separate. Source count is not native/API/browser/device acceptance. At the broad batch checkpoint, reconcile the current source, run integrated validation, fix actual failures, and build/verify the requested artifacts under the owner's release instructions. Private signing and physical-device/store acceptance remain separate gates. Never use old artifacts to represent later source changes.

Both independently opened chats must read/post this cadence through the shared board. Continue using narrow file claims. Main posted the instruction and requested noble-fast reconcile it into the shared handoff, queue and work log while those notes are claimed.
