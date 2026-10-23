# Offline guest editing — internal tester guide

October 23, 2026. This guide describes the implemented guest flow. Use the latest verified internal APK named in SESSION_HANDOFF; build and device acceptance are separate evidence.

1. Open Honorable and choose **Edit without an account**.
2. Choose a photo or video with the system file picker. Broad library permission and media indexing are not required for this entry.
3. Use the existing local editor. Basic adjustments, framing and copy export are available. Video tools include clip operations and manual captions/SRT where supported by the existing editor.
4. Save/export a **new copy** to a location you can find in Files. Check the saved output before sharing it. The app protects imported originals; an unavailable original/provider can prevent export rather than risk overwriting it.
5. Return to the guest screen to open another source. Use its sign-in action to return to the account entry screen.

Guest editing does not include Seran search, credits, paid/AI actions or encrypted account drafts. Guest lifecycle recovery is bounded local activity state, not a durable saved project. Export a copy to keep the result. Account sign-in still requires the hosted account service, which is not configured in this internal build.

## First device acceptance pass

Use disposable test media. Confirm picker cancellation and retry, photo crop/color/rotation and saved dimensions, video trim/split/order/audio and exported playback, caption timing, SRT output, background/foreground and rotation recovery, and permission/provider failures. Confirm AI/account draft tools stay unavailable in guest mode and that original bytes remain unchanged.

No physical-device pass has been performed by the development team. The current APK is public-debug-signed for internal testing; production signing and store distribution remain separate work.
