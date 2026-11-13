# Neural object eraser — integration in progress

The owner requested more actual AI capabilities through established open-source projects. This adds a mobile object-removal flow, not another manually sampled clone tool or counted preset.

The official MIT MI-GAN end-to-end ONNX pipeline from Picsart AI Research is reused directly. Its graph includes crop/resize, preprocessing, neural reconstruction and postprocessing. Pinned official model revision406830d0fa60666da0071c342ad2fbc8f30c5c64;28079181bytes SHA2566f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b. Full MIT weights license/provenance ship under assets/inpaint. No retraining, server or image upload.

Current PhotoEraseModel validates uint8 NCHW RGB and official binary mask convention (255known,0hole), bounds inputs and reserves nativeRunOptions for Stop. It preserves every unpainted working-image ARGB pixel exactly, even if the model modifies those pixels internally. Painted regions can acquire newly generated opaque content; output is a reconstruction, not recovery of the hidden original scene. Three portable JVM cases pass; actual pinned pipeline ran on the publicNASAfixture, changed7738painted pixels in a2.8s host observation, retained every unpainted pixel and left source/mask arrays unchanged.

Remaining before release: touch mask editing, full before/after review, owned generated-copy opening/export and lifecycle/resource tests, current full app/APK/device checks. The Root entry is not yet wired and this is not a completed user flow or phone performance/quality claim. No new500-feature target or count. Main release lane continues implementing this while the coherent depth/creative workspace integration is compiled.
