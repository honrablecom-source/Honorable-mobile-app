# C071 — Trim transparent borders into a separate image

**1 new experimental source workflow; 0 separate fixes or scaffolding.** Host and API checks pass. Android runtime and device acceptance remain pending.

Photo editor → Transform → Trim transparency finds the smallest rectangle containing every nonzero-alpha pixel in the edited render. It removes only completely transparent outer rows and columns. Faint antialiased edges stay; opaque white or black borders are not removed. A fully transparent image or an image already touching every outer edge gives an explanation instead of creating a copy.

Explicit **Create trimmed copy** opens a new local guest photo workspace with a flattened PNG at editor preview resolution. The original photo, current editable composition and its Undo history stay in the original workspace. The new workspace starts with the trimmed PNG; it does not inherit upstream layers or account privileges. Save an image project or export PNG to keep that result, because the starting cache copy is temporary. Original-source provenance is carried forward through the existing protected-source mechanism.

The scan keeps one reusable pixel row, bounds dimensions/allocation and retains alpha values as low as 1. It never detects or trims by visible color, never uploads pixels, and adds no dependency or edit-schema field. Publication uses the existing bounded private-image writer with cleanup on encoding failure, stale state or failed launch. Actual Android pixels, PNG/provider bytes, UI/foreground behavior, navigation, persistence and source integrity still require device acceptance.

Implementation: `PhotoAlphaTrim.kt`, `PhotoEditorActivity.kt`. Tests: `PhotoAlphaTrimTest.kt`. Evidence: `.context/photo-alpha-trim/verification.json`. No full Gradle/native/APK build is authorized at this milestone; retain the owner's feature-batch cadence.


Nine new host cases cover asymmetric bounds, faint alpha, transparent hidden RGB, full/edge bounds, one-row buffer reuse, allocation refusal, cancellation, 200 synthetic random masks and paused-Activity admission before UI access. The complete photo/shared API compilation covers 72 source files; 287 host tests across 37 classes pass. Final Activity and trim-test overrides precede the verified base jars, so the results exercise the current foreground guard and test rather than stale compiled classes. All source/dependency hashes are retained.

Pausing the Activity invalidates the captured request generation. A paused screen refuses the dialog; pausing during work cancels publication even if the same photo is shown again. Invalid or stale work leaves the current edit unchanged and deletes any generated temporary file. Error messages explain empty/opaque/no-border cases without exposing internal file errors. The new button has its own transform row, preserving space for existing controls. These UI and lifecycle behaviors are implemented in source; actual dialog, bitmap, PNG, foreground/navigation and provider behavior have not been run on Android. No full APK build or claim of production readiness.
