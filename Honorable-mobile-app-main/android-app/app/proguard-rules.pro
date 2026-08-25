# ONNX Runtime ships consumer rules for its Java/native bridge. Keep only native
# method names needed by JNI rather than disabling obfuscation for the whole SDK.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# These model-facing types are instantiated directly (never by reflection).
-keepclassmembers enum app.honorable.search.MediaKind { *; }
