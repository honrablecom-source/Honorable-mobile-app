# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# React Native and ONNX ship consumer rules. Preserve only the JNI/native entry
# points and the explicitly registered bridge surface; the search implementation
# remains eligible for R8 optimization and identifier obfuscation.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.honorablemobile.HonorableNativePackage { public *; }
-keepclassmembers class com.honorablemobile.HonorableSearchModule {
    @com.facebook.react.bridge.ReactMethod <methods>;
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
