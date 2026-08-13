# Flowframe (com.gba.emulator.shell) — R8/ProGuard rules for minify-enabled builds.

# Preserve JNI native bridge classes and methods from R8 obfuscation/stripping.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.gba.emulator.shell.GbaCoreBridge { *; }
-keep class com.gba.emulator.shell.GbaRuntimeBridge { *; }
