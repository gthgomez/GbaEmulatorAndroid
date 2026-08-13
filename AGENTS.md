# AGENTS.md — GbaEmulatorAndroid (Gemini 3 Flash Override)

> Inherits from [root AGENTS.md](file:///C:/Workspace/Project_Android/AGENTS.md). See [CLAUDE.md](./CLAUDE.md).

## Gemini-Specific Risks
- Hallucinated JNI method signatures (name mangling errors → UnsatisfiedLinkError)
- Incorrect CMakeLists.txt NDK/ABI configuration (wrong target API, cross-compilation)
- Confusing SurfaceView lifecycle with Compose (GameViewportSurface uses Canvas.drawBitmap)
- Hallucinated Oboe audio API bindings (wrong callback signatures, ring buffer init)
- Incorrect SAF document URI handling for ROM loading (header validation required)

**Verification gate:** `./gradlew assembleDebug` and run bridge self-test on device
