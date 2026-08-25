# Subscription entitlement foundation

The authoritative policy is `android-app/app/src/main/java/app/honorable/Entitlements.kt`. Tiers inherit monotonically from Free through Ultimate. Storage is represented in integer bytes (GiB units), video allowance windows retain a stable external window ID, count both videos and duration, and never reset merely because another video starts.

`TrustedEntitlementState.effectivePolicy()` deliberately demotes every unverified state to Free. React Native receives a display DTO from the native module; changing JavaScript or AsyncStorage cannot elevate authority. Google Play Billing/server verification is still unconfigured, so the native module currently returns unverified Free. StoreKit remains an interface-stage future macOS task.

Implemented enforcement in the React Native Android search boundary:

- Free: photo/OCR search can use the real engine; video candidates and explicit video queries are locked.
- Plus policy: permits semantic video and representative-frame ranking but withholds exact timestamps.
- Pro+: permits exact indexed video timestamps.
- Super/Ultimate: entitlement and navigation gates exist; Compare, Find Similar, Find Over Time, Memory Connections, Ask Your Library, and Visual Comparison remain `COMING_SOON`. No result is fabricated.

The UI shows all five canonical plans and quotas without prices. Pricing stays `COMING SOON` until store-localized product data is configured. The usage screen reads native entitlement state. Storage usage, search usage, and active video-window consumption require real repository/billing data and are not fabricated.

Current limitations: the bridge's verified state provider is a secure placeholder returning unverified Free; Google Play product loading/purchase/restore/server verification and StoreKit 2 do not yet exist. Storage-byte measurement and quota checks before indexing are not implemented because the current `MediaRecord` does not persist source byte size. Family membership is policy-only (up to five total) and never implies media sharing.
