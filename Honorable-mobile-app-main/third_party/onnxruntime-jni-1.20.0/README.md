# Exact ONNX Runtime1.20 Java/JNI sources

Unmodified MIT-licensed upstream Java/native/include files are pinned by provenance.json. Java sources generate JNI headers only; the published AAR classes.jar remains byte-identical. The existing published inference libraries also remain byte-identical. Only the two64-bit JNI bridge libraries are rebuilt using the official16KB linker fix. NNAPI remains enabled, matching the original Android JNI imports. See scripts/ort-jni16kb.py and docs/ORT_JNI_16KB.md.

Retain LICENSE and every original Oracle/Microsoft copyright header. This is a compatibility backport, not a new inference engine or feature.

Full MIT terms and collected original copyright notices are added to assets/ort-jni16kb in generated AARs/APKs. All other retained distribution entries are byte-identical apart from the two rebuilt JNI bridges.
