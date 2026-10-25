# Imported photo protection — October 24, 2026

Work in progress; not part of the published APK. No new user-facing workflows.

The photo export path now accepts up to 16 validated local content-URI originals from a private creation activity. It opens all originals read-only, compares each open file identity with the chosen destination, and refuses matching aliases before truncation. Missing read access fails closed. Existing source protection remains.

Shared Android validation: 115 JVM tests passed, zero failures/errors/skips. Three new provenance validation cases cover malformed/nonlocal URIs and resource limits. Two new Android integration cases cover a secondary original through a hard-link alias and successful new-copy export. They require device execution; none has been performed.

The first RN integration compile failed because the previous H: build mapping was missing. Mapping restored; retry passed (BUILD SUCCESSFUL). The two Android integration cases compiled, but have not run on a device. Evidence: outputs/protected-originals-integration-retry.log. No replacement APK has been built for this change.

The miniPaint candidate remains outside the application. Import, editable persistence and PNG handoff are not integrated. No new AI engine or 90-feature completion claim.
Owner workflow update: implement the next feature batch together, run lightweight checks during development, and compile the integrated batch once. Do not build a full APK per feature.
