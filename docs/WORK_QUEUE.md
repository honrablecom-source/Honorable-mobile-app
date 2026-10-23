# Engineering queue

## Latest built increment — phone acceptance pending

- [x] **GUEST-01** — Offline basic guest photo/video editing; build/test verified, physical acceptance pending. [[HONORABLE_LOCAL_EDITING_READINESS_CHECKPOINT]].
- [x] Fix signed-in denied/empty-library editor access and subtitle grant rollback; actual tests/build recorded in checkpoint.

- [x] **CAP-01** — Timed captions: bounded model, editor controls, shared preview/export, undo and compatible recovery. Built and checked; not device-accepted. Evidence: [[HONORABLE_CAPTIONS_CHECKPOINT]].
- [x] **CAP-02** — SRT subtitle import/export: strict parsing, source protection and explicit timing semantics. Built and checked; not device-accepted. Evidence: [[HONORABLE_CAPTIONS_CHECKPOINT]].

## Next useful work

- [ ] Evaluate local speech transcription and editable transcript generation after caption infrastructure works. Requires a real runtime/model and measured output.
- [ ] Improve durable creative project interchange and recovery from the feature register.
- [ ] Add a genuine local image-generation worker after hardware/runtime/model and license checks; avoid pretending prompt controls are a generator.
- [ ] Continue photo masks/layers and video compositing using accepted canonical project state and matching render/export paths.

## Deferred / external gates

- [ ] Android physical-device media, output, lifecycle and accessibility acceptance — owner has no phone.
- [ ] Hosted account API and production sign-in — billing/hosting deferred by owner.
- [ ] Private signing, store billing and distribution.
- [ ] iOS build/native parity and device validation — macOS/Xcode work required.

## Last completed

- [x] Recover the same committed repository after source deletion (722 tracked files recovered).
- [x] Photos-inspired core screen revision; verified internal APK and pushed `6f3a8d7`. UI work now paused.

Only check an item after its actual acceptance evidence is recorded. Experimental and built-but-not-device-tested work must say so.
