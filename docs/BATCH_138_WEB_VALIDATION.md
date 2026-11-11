# Source 138 web integration checkpoint

Status: all 17 selected browser suites pass across retained runs; exact tested assets published and verified. Initial full-run failure remains recorded. This is not native or device acceptance.

The register reached 138/500 distinct source workflows, 100 beyond the owner's baseline of 38. New feature work is frozen for the coordinated integration checkpoint. This checkpoint adds **0 features, 0 separate fixes and 0 scaffolding**. The remaining 362 workflows are not complete.

## Frozen inputs

Snapshot: `.context/noble-fast-web/batch138/web-inputs.json`.

Production source SHA-256: `150524348c1fba5a2a009fc71f04a09d6d052b04399540847a5fe95e438c36bc`.

The snapshot covers 163 production source/notice inputs and separately records 147 test files plus the runner hash. Current source was compared with the snapshot during integration and matched. The isolated output manifest was verified against current source and actual output bytes. These checks do not establish a complete browser pass.

## Validation scope

The initial `node scripts/noble-web-checks.cjs` run (session 36396) terminated with exit 1 at its 15th browser suite, nine-slice. It has passed all 565 drawing unit cases, 70 Node product/tooling cases, isolated bundle generation and manifest verification. Browser validation runs 17 selected suites: schedule, despeckle, redaction, luminance mask, displacement, seamless tile, normal map, distance field, palette image, channel pack, channel split, sprite slicing, anaglyph, Cube LUT, nine-slice, reference tones and panorama. This is not every historical browser entrypoint in the repository.

Full logs are retained under `.context/noble-web-checks/`; earlier runs are archived under its `runs/` directory. A pre-existing focused receipt is not evidence that the full run passed. Final evidence must match this run, frozen source and all 17 completed browser stages.

## Asset/native gate

Tracked Android drawing assets now contain the exact verified isolated output. The old Git-clean tree was backed up and byte-compared before replacement; source and every generated file were verified after copying. The prior baseline predates the manifest, so rollback preservation was checked by complete file-set/hash comparison rather than requiring a historical manifest.

Noble-main owns native integration/build preparation; noble-fast owns this web validation and asset preparation. Physical Android WebView, native PNG handoff/provider/editor/save-copy, lifecycle/accessibility, device memory/performance and store readiness remain unverified by browser tests. No personal media or queries are uploaded. Search ranking, holdout and credit/catalog/server ledger behavior are outside this change.

## Initial integration failure and diagnostic

The first 14 selected browser suites passed. The noisy 1024×1024 JPEG nine-slice case targeting 1023×1024 reached the application's unchanged 30-second processing deadline instead of the expected exact-target PNG-size refusal. The browser then timed out waiting for the size-refusal message. The app showed “Nine-slice resizing took too long. Try simpler artwork.” This is a failed expected outcome, not a full pass.

Full initial-run logs/receipt, bundle manifest and nine-slice failure screenshot/body/stack are preserved in `.context/noble-fast-web/batch138/full-attempt-1/`. Current host CPU pressure was approximately 97% at observation; this is evidence of contention, not proof of the failure's cause. Noble-main temporarily suspended its own cold Gradle daemon. The unchanged focused nine-slice rerun (session 1843) passed with the original assertions and application deadline. No production source or tests have changed. Subsequent unchanged focused reference-tone (session 87729) and panorama (session 29747) runs passed. All selected suites are covered across runs; the initial full runner did not pass in one run.

## Verified publication

Receipt: `.context/noble-fast-web/batch138/web-integration-receipt.json`.

- 565 drawing unit cases and 70 Node cases passed without skips.
- All 17 selected browser suites passed across the retained initial run and unchanged focused runs. `singleFullRunPassed` remains **false**; the initial nine-slice timeout is retained. Reduced competing native load was associated with a passing rerun; device performance remains unknown.
- All 163 production/notice inputs, 147 test hashes and runner hash still match the frozen snapshot. No source/test/deadline/assertion change was made.
- The manifest is byte-identical across the initial and final tested bundles. All 271 generated files were copied exactly to `Honorable-mobile-app-main/mobile-react-native/android/app/src/main/assets/drawing/` and reverified.
- Manifest SHA-256: `9a7dff9a2c8195a8c69ebd2231f968e22f5081d4b35da2cdedf15710c30c519a`.
- Rollback tree: `.context/noble-fast-web/batch138/previous-tracked-drawing/`.

The verified asset gate and exact hashes were posted to Noble-main, together with the instruction that it can resume its own suspended daemon and copy these assets into its isolated native build. Native build/artifact/device outcomes remain that separate owner's responsibility. New feature work remains frozen until the integration checkpoint is reconciled. Goal500 remains active at138/500. This milestone adds0new features,0separate fixes and0scaffolding.
