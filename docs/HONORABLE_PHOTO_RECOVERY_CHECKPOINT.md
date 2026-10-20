# Photo recovery checkpoint — October 19, 2026



## Implemented and build-verified



Photo activity state now carries bounded scalar adjustments, up to 50 undo/redo entries, compare mode, selected adjustment and frozen settings for a pending save picker. Recovery requires the same unexpired account, API and source identity. The saved bundle contains a hash and scalar edits, not account IDs, source URIs, tokens, pixels or person masks. Person masks are recomputed locally before a cutout preview/export. An account switch during loading cannot rebind an older pending snapshot. A save result without frozen settings is rejected and requires a new Save copy action. Started exports are not automatically replayed after recreation; check Files for interrupted/partial copies and explicitly save again.



Scene enhancement owns its analysis image until completion and rejects a result after edits change. This is lifecycle recovery, not persistent photo projects. Android device rotation/process/picker, segmentation pixels and export providers still require physical validation; no phone is available.



## Local efficiency tool



Installed the portable official RTK OSS 1.0.1 Windows release under C:/Users/Shadow/Desktop/honrabl/.tools/rtk-oss-v1.0.1. Archive SHA-256 matched the release API digest: 0e53e2eca471e15fbd3636e64e6bc0b182cbc278ed0339c80450a2dd03f4cd6b. Source: https://github.com/rtk-ai/rtk. No global hooks, PATH, assistant configuration, proxy or model routing changed.



On one existing build log, `rtk log` reduced output from 27,881 to 1,347 bytes (95.2%). Bytes are not billed tokens and this is not a measured whole-credit saving. It misclassified Gradle configuration-check task names containing Errors as errors, so raw logs, exit codes and test XML remain authoritative. Prefer RTK for routine Git/log inspection and inspect raw diagnostics for failures. Codecmd was not confidently identified; no arbitrary similarly named package was installed.



## Optional unshrunk local Android iteration

The opt-in `-PhonorableFastInternalBuild=true` skips code/resource shrinking for internal APKs. The default is unchanged; production release shrinking/signing policy remains in place. This checkpoint uses the larger unshrunk internal artifact. Its first build took 3m54s, compared with 3m48s for the preceding optimized build of different source; these are not controlled measurements and no overall build-time saving is established. Device instrumentation and optimized builds are separate validation gates.

## Next



Multiple video sources and persisted source access, then transitions/titles and output validation. Advanced generation needs an actual engine and an accepted privacy/cost approach; hosted billing remains deferred. Rigging, layers/nodes/3D/code execution and iOS are still incomplete. White-outline mobile UI remains unaccepted; no investor-ready claim.


## Final validation and artifact

70 shared Android JVM tests, 16 RN native JVM tests and 67 Node checks pass. Android instrumentation compiles but has not run. The combined ARM64 internal APK passed package/non-debuggable/ABI checks, native and JS integration markers, bundled model provenance, approved certificate and ZIP alignment checks. Source fingerprints were verified against the build inputs. Earlier optimized builds passed before the last save guards and opt-in build configuration; the latest delivered artifact uses the explicit unshrunk mode.

APK: Honorable-1.0.3-photo-recovery-arm64.apk

SHA-256: `7176f55e3a77c3428b5b1fab6b6fc566f8e0f364262b0b75daf3398a40f73e80`

Public debug certificate; internal testing only. The hosted account API is absent; Google configuration alone does not make native sign-in ready. UI acceptance, device behavior and store/iOS gates remain open. Both previous c7edca1 GitHub Android builds were independently confirmed successful (39335645333, 39335639560); new commit CI is a separate result.
