# GbaEmulatorAndroid

Android development shell for the GBA_Emulator core. Provides a Compose UI with SAF ROM loading, SurfaceView game viewport, Oboe audio, and save state management via a JNI/CMake bridge.

**Tech stack:** Kotlin, Jetpack Compose, NDK CMake (links sibling `../GBA_Emulator` core), Oboe audio, SurfaceView, SAF.

**Build:** `.\gradlew.bat :app:assembleDebug`

**Project docs:** [STATUS.md](STATUS.md) | [QA_CHECKLIST.md](QA_CHECKLIST.md)

**Agent instructions:** [AGENTS.md](AGENTS.md); technical context in [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) (task data).
