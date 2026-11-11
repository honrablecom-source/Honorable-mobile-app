# C109 — Reviewed CMX3600 video cut-list transfer

Video inspector → Project → Copy CMX3600 cut list creates a reviewed video-only edit decision list for manually conforming original source cuts in another editor. Choose 24, 25 or 30 integer non-drop fps, inspect exact text, then explicitly Copy EDL. Ordered cuts, intentional repeated source ranges and stable first-use reel identifiers are retained. This is one new source workflow, not one feature per frame-rate choice.

Source timestamps start at zero; embedded camera timecode is not read. Each in point rounds up and out point rounds down to the chosen whole-frame grid. The review reports adjusted-cut count and output-frame duration. Tiny ranges without a whole frame, non-1x speeds, empty/over-999-event lists, over-32-source lists, invalid source IDs and source/output timecode wrap at 24 hours are refused. There are no silent dropped events, invented speed effects or record gaps. Relink each reel manually from the included internal source-ID mapping.

This cut-list format deliberately omits Noble captions, titles, color/layout/motion/fades, original clip audio and soundtrack. Review discloses those omissions, inward rounding, lack of embedded-timecode support and unverified external-editor compatibility. It never reads or includes original media bytes, URIs, filenames, captions or private labels. Copy explicitly discloses system clipboard/sync access and requests sensitive clipboard metadata; that metadata is not a privacy guarantee.

The existing tracked inspector/full-session review helper gates both frame-rate choice and final copy. Copy requires the current project/selected clip/session/source/range/generation, consumes successful approval and cannot replay after success. Dialog dismissal clears the prepared text and preview. No project mutation/history, original-media write, upload, source catalog or renderer change is involved.

## Verification

**13 JUnit tests pass** against the actual production `VideoEdl.kt`, compiled with temporary Kotlin 2.1.20/JUnit 4.13.2. Cases cover exact event syntax, source/record in/out, first-use reels and repeats, inward rounding, supported rates, invalid rates/count/speeds/times, empty frame ranges, 24-hour source/output refusal, injection/source limits, owned immutable mapping, unchanged inputs and locale-independent ASCII. A deterministic generated-case test independently parses 1,800 exported events and checks source containment, positive frame durations, stable row structure and contiguous record boundaries.

**3 Node source-contract tests pass:** discoverable route/current chooser, metadata-only formatter input and disclosures, and guarded sensitive clipboard write/cleanup without project mutation. They verify source routing; they do not execute Android dialogs or clipboard services.

Reproduction with temporary compiler and Maven test jars:

```sh
java -cp '/tmp/noble-kotlin-2.1.20/kotlinc/lib/*' org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -kotlin-home /tmp/noble-kotlin-2.1.20/kotlinc -cp /tmp/noble-junit-4.13.2.jar \
  Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile/VideoEdl.kt \
  Honorable-mobile-app-main/mobile-react-native/android/app/src/test/java/com/honorablemobile/VideoEdlTest.kt \
  -include-runtime -d /tmp/noble-video-edl-tests.jar
java -cp /tmp/noble-video-edl-tests.jar:/tmp/noble-junit-4.13.2.jar:/tmp/noble-hamcrest-1.3.jar \
  org.junit.runner.JUnitCore com.honorablemobile.VideoEdlTest
node --test scripts/video-edl.test.cjs
```

**Experimental source implementation.** Actual Activity/API compilation, Android UI/clipboard/scroll/accessibility/lifecycle, real-media external-editor import/relink/timebase acceptance, full app/native/Gradle/APK and device/store acceptance are unverified. Temporary compiler/test jars came from JetBrains and Maven Central; no project dependencies were changed. Do not treat these tests as a successful conform in a third-party editor or as proof of the source media's actual frame rate. New features:1; separate fixes:0; scaffolding:0. Batch-build checkpoint stays approximately138 source workflows.
