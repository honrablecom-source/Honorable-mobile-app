# Local editing readiness — October 23, 2026

Owner-approved increment: **1 new workflow — offline guest photo/video editing**, plus **1 usability fix** for signed-in users with an empty or inaccessible search library. React Native source checks pass; combined native validation and APK verification remain pending. Physical-device acceptance remains separate.

## Confirmed blockers and scope

- The previous first-run path wrapped all app routes in `AccountGate`; it could not offer local editing when account sign-in was unconfigured. The latest prior APK checkpoint reported `signInConfigured=false`; see [[SESSION_HANDOFF]] and [[HONORABLE_CAPTIONS_CHECKPOINT]].
- `LibraryGate` also required media permission and a nonempty index before exposing Studio, despite an existing system document picker that can grant access to one selected file.
- The owner explicitly approved offline guest editing. Account authorization, search charging, credits, AI access and authenticated editor entry remain distinct from guest local editing.

## React Native implementation

- `AccountGate` offers **Edit without an account** after account restoration, and explains unavailable Google sign-in configuration. The guest screen has only **Edit a photo**, **Edit a video** and **Back to sign in**.
- Guest entry does not mount the normal gate children: library provider, search provider or app navigation. The existing outer account provider still restores established sessions; guest mode does not establish an account or spending authority.
- `openWorkspace` defaults to existing account access. Explicit guest access uses only `editPhotoGuest(uri)` or `editVideoGuest(uri)` after the user selects a file. Native implementation and basic-tool restrictions belong to the native owner.
- Cancellation leaves the guest screen available; errors surface through the existing alert and release the busy state. A pending picker disables duplicate launches and returning to sign-in until that request settles.
- Signed-in users can also open either existing authorized editor directly from the media-permission/empty-index screens. Search-library setup remains unchanged.

## Validation

- Added 11 guest-flow cases, two guest bridge cases and seven library-gate cases. These cover dedicated native routing, no account/search/spending calls from guest navigation, cancellation, errors, busy protection, returning to sign-in, retained authenticated routes and retained library gates.
- Full React Native Jest: **52 passing cases / 11 suites** (20 added cases). TypeScript: **pass**. Full lint: **0 errors / 36 existing warnings**. An initial new-library-test failure was a missing icon in its test mock; the scoped mock was corrected before the passing final run.
- `git diff --check` passes. No app-source changes follow these checks without notifying the build owner.
- No Gradle or APK validation was run by the RN owner. The build owner will record combined native checks, matching source inputs and artifact evidence after source freeze.

## Remaining acceptance

Native guest restrictions, full packaging and device behavior must be verified on the combined source. Guest local editing does not establish hosted sign-in, AI engines, paid services, store signing or iOS readiness. No Android phone is available; follow [[HONORABLE_ANDROID_DEVICE_CHECKS]] when one is available. Final milestone updates belong in [[SESSION_HANDOFF]] and the linked checkpoint, following [[DECISIONS]].


## Final verified milestone

**1 new user-facing workflow:** explicit offline guest basic photo/video editing. **2 fixes:** authenticated document-picker access behind empty/denied-library gates, and subtitle permission rollback/cleanup. **0 new AI engines.** First launch can enter isolated guest pickers without the unconfigured account service. Native guest mode validates readable content sources, denies AI/account drafts, isolates local lifecycle state and avoids account telemetry/reads. Signed-in server authorization and credit/search policy remain intact.

Fresh 105 shared + 89 RN-native JVM cases pass (14 added); 52 RN tests / 11 suites pass (20 added), TypeScript passes, lint 0 errors / 36 existing warnings. Integration compiles only. Full ARM64 internal APK independently verified against 493 frozen inputs, guest/code/model/manifest markers, certificate and ZIP alignment: Honorable-1.0.3-usability-arm64.apk; SHA-256 7699ed4cfd0ae31184cb4b68ef9a29bfe97f7bea0285830ab7a9a418bdb36225. Public debug signing; signInConfigured=false, guest editing available. Physical-device media/output/lifecycle, hosted sign-in, private signing/store, full AI generation and iOS remain open. See HONORABLE_LOCAL_EDITING_READINESS_CHECKPOINT.md and GUEST_TEST_GUIDE.md.


Source publication is recorded separately by the post-commit APK verifier and GitHub review record.
