# Person background checkpoint — October 20, 2026

Feature count for this increment: **2** — adjustable AI person-background blur and neutral background replacement (white/black/gray). These extend the existing on-device person segmentation; they do not generate photos or video. Continue the existing repository, mobile UI and editing engines.

## Implementation

Photo → Assist → AI person background offers Blur background, White/Black/Gray background and Restore original background. The blur dialog has strength, explicit Apply and Cancel. Tools share the existing model-owned input, stale-edit guard, Undo/Redo, Compare, Reset and protected PNG-copy exporter. A person cutout and a filled/blurred background are mutually exclusive; switching tools retains the reusable source-aligned matte.

A bounded 384-pixel proxy supplies a three-pass soft blur. Foreground color is excluded from the blur input using the person matte, limiting person-color halos. Final soft-alpha composition runs row by row, mapping the source matte through the same center crop and quarter rotation as the photo. Preview and PNG export use the same renderer; existing tone edits are applied before the background operation. Solid backgrounds stay their selected neutral color. Source pixels/mattes are not changed. Beta person segmentation still needs hair/edge/no-person and performance acceptance on real devices; generic object selection and depth-aware lens bokeh are not claimed.

Photo recovery selects v3 only when a current/history/pending-export edit has a background operation. Older v1/v2 remain readable and use their existing serialization when sufficient. The existing scope, 50-step histories, frozen pending export, byte limit and local mask reconstruction remain. No masks, source paths or pixels are serialized into the lifecycle payload.

## Validation

90 shared Android JVM and 45 RN-native JVM tests pass (135 total; 0 failures/errors/skips), including nine new shared regression cases. Three new Android bitmap integration cases compile only; no instrumentation execution. Complete ARM64 internal APK built and independently verified for package, bundled JS/models/native background markers, approved certificate and ZIP alignment; 460 frozen build-input hashes match. Artifact: Honorable-1.0.3-person-background-arm64.apk. SHA-256: b61b0a961c0301a44a6bbb57032cb992fa9bbc7981eb7d9709b13c2878202776. Opt-in unshrunk internal mode and public debug key; no store-readiness claim.

During review, alpha handling and premultiplied sampling were corrected; the initial frozen-input guard stopped before packaging. Final checks and packaging used a fresh immutable snapshot of the corrected source. Earlier logs are retained separately. RN UI/backend code is unchanged in this native batch, so preceding TypeScript / 28 RN / 67 backend checks remain historical evidence, not newly rerun here. No protected benchmark/holdout was inspected or executed.

## Generation feasibility and next work

Checked the real machine before installation: NVIDIA RTX 2000 Ada reports 15,356 MiB GPU memory, but installed PyTorch is 2.14.1+cpu with CUDA unavailable. The system drive has approximately 3.5 GiB free and no separate data drive is available. An isolated CUDA runtime plus generation weights and build headroom do not fit safely. No large model/runtime download or global package modification was started. No paid hosting, account-service deployment or private-media upload was enabled. Actual prompt/reference/sketch generation, video synthesis, inpainting, rigging and general creative chat remain incomplete.

RTK OSS remains in use for compact Git/log previews; raw logs, process exit codes and JUnit XML remain authoritative. No total-token or credit-saving percentage is claimed. The owner requested a feature count in every completion update; recorded in AGENTS.md.

Next: finish this APK checkpoint, then resume video AI/creation and generation integration when adequate disk space or an owner-selected existing runtime is available. The absent hosted account API, unavailable Android phone, owner visual acceptance, private store signing and macOS/iOS validation remain open.
