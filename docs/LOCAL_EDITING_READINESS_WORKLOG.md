# Local editing access and caption correctness

October 23, 2026. Status: **implementation and validation in progress; no completed new feature claimed yet**.

The owner requested progress toward actual usability and explicitly approved offline basic guest editing. This increment keeps the existing app and design. It addresses three related paths: an explicit guest entry into the local basic photo/video editors, document-picker editing for authenticated users behind denied/empty-library gates, and durable subtitle permission ownership after asynchronous SRT parsing.

## Boundaries

- Guest editing uses separate bridge methods and an explicit native access mode. Account search/navigation, credits, AI tools and account-scoped drafts remain gated. Guest edits export copies; native physical-device behavior remains unverified.
- Existing account entry points retain authorization, and missing native access mode means account mode. Unknown modes fail closed. Account/server credit policy and Seran ranking stay unchanged.
- Subtitle imports acquire durable access only after the result is current. A refused atomic edit releases only a newly acquired grant; previously owned access remains available to other projects/drafts.
- No billing/hosting, paid services, personal-media uploads, holdout benchmark work or user-app closing.

## Team and validation plan

`caption_review` owns native guest boundaries, caption permission correctness and native regressions. `obsidian_notes` owns RN guest/library entry points and RN regressions. `android_build` independently reviews their integrated boundaries, exclusively runs Gradle, freezes all app input hashes and publishes exact evidence.

Both Android source sets change, so shared test reuse is invalid. Fresh shared and RN-native JVM cases, shared/native integration-test compilation, RN tests, TypeScript/lint and a complete ARM64 internal APK are required on the frozen source. Instrumentation will compile only; no phone is available. Existing caption evidence is preserved under its original filenames; this increment uses `Honorable-usability-*` evidence.

Final feature/fix counts, test totals, artifact/source identity and remaining gates will be entered only after verification. See [[SESSION_HANDOFF]], [[WORK_QUEUE]] and [[TEAM_WORK]] for the working handoff.


## Final verified milestone

**1 new user-facing workflow:** explicit offline guest basic photo/video editing. **2 fixes:** authenticated document-picker access behind empty/denied-library gates, and subtitle permission rollback/cleanup. **0 new AI engines.** First launch can enter isolated guest pickers without the unconfigured account service. Native guest mode validates readable content sources, denies AI/account drafts, isolates local lifecycle state and avoids account telemetry/reads. Signed-in server authorization and credit/search policy remain intact.

Fresh 105 shared + 89 RN-native JVM cases pass (14 added); 52 RN tests / 11 suites pass (20 added), TypeScript passes, lint 0 errors / 36 existing warnings. Integration compiles only. Full ARM64 internal APK independently verified against 493 frozen inputs, guest/code/model/manifest markers, certificate and ZIP alignment: Honorable-1.0.3-usability-arm64.apk; SHA-256 7699ed4cfd0ae31184cb4b68ef9a29bfe97f7bea0285830ab7a9a418bdb36225. Public debug signing; signInConfigured=false, guest editing available. Physical-device media/output/lifecycle, hosted sign-in, private signing/store, full AI generation and iOS remain open. See HONORABLE_LOCAL_EDITING_READINESS_CHECKPOINT.md and GUEST_TEST_GUIDE.md.


Source publication is recorded separately by the post-commit APK verifier and GitHub review record.
