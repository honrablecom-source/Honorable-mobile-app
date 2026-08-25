# Android outputs

- `honorable-debug.apk`: debug-signed development APK.
- `honorable-release-unsigned.aab`: compiled release bundle without a production signature.

The AAB is not Play Store ready. Generated binaries are ignored by Git to avoid
inflating repository history; CI also publishes them as workflow artifacts.
