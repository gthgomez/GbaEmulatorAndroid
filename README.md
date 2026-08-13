# GbaEmulatorAndroid

Android development shell for the GBA_Emulator core. Provides a Compose UI with SAF ROM loading, SurfaceView game viewport, Oboe audio, and save state management via a JNI/CMake bridge.

**Tech stack:** Kotlin, Jetpack Compose, NDK CMake (links sibling `../GBA_Emulator` core), Oboe audio, SurfaceView, SAF.

**Build:** `.\gradlew.bat :app:assembleDebug`

**Detailed docs:** [CLAUDE.md](CLAUDE.md) | [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | [STATUS.md](STATUS.md) | [QA_CHECKLIST.md](QA_CHECKLIST.md)
