# GbaEmulatorAndroid Status

**Last verified:** 2026-08-01
**Status:** active development
**Confidence:** high

## Purpose

Android frontend shell application for the portable `GBA_Emulator` C++ core, providing Jetpack Compose UI, SAF ROM file loading, SurfaceView rendering, Oboe low-latency audio, and JNI/CMake bindings.

## Current State

The app is built as a dev shell interfacing with the sibling `GBA_Emulator` core via NDK CMake JNI bridge (`android_core_bridge`). Incorporates SurfaceView rendering, on-screen touch D-pad/A/B controls, and SAF storage access.

## Verified Capabilities

- JNI bridge compilation and native C++ link to sibling `GBA_Emulator/` CMake target.
- Storage Access Framework (SAF) ROM file selection and Nintendo logo header validation.
- Low-latency Oboe audio ring-buffer integration.
- SurfaceView 240x160 GBA display rendering.

## Recent Evidence

- `QA_CHECKLIST.md` specifies 7-section dev shell testing checklist including self-test step budget checks.
- Bridge self-test procedures documented in `GBA_Emulator/docs/android-device-soak-checklist.md`.

## In Progress

- Frame-pacing optimization and audio underrun prevention on high-refresh-rate Android screens (90Hz/120Hz).

## Blockers

- Requires physical Android device soak testing per `GBA_Emulator/docs/android-device-soak-checklist.md`.

## Risks and Unknowns

- Thread synchronization and JNI zero-allocation array buffer copying under 60 FPS strict frame deadlines.

## Verification

- `.\gradlew.bat :app:assembleDebug` setup present.

## Next Actions

1. Perform JNI bridge self-test run on connected Android test device.
2. Execute device soak checklist (`GBA_Emulator/docs/android-device-soak-checklist.md`).
3. Verify Oboe audio stream stability during long session runs.

## Evidence Sources

- [README.md](file:///C:/Workspace/Project_Android/GbaEmulatorAndroid/README.md)
- [QA_CHECKLIST.md](file:///C:/Workspace/Project_Android/GbaEmulatorAndroid/QA_CHECKLIST.md)
- [GBA_Emulator/docs/android-device-soak-checklist.md](file:///C:/Workspace/Project_Android/GBA_Emulator/docs/android-device-soak-checklist.md)
