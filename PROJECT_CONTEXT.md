# GbaEmulatorAndroid

Dev shell for `GBA_Emulator` controlled beta track. Not a store-ready product.

## Stack

- Kotlin, Jetpack Compose, minSdk 26, targetSdk 36
- NDK CMake → `libgbaemulator.so` linking sibling `../GBA_Emulator` core (bridge subset)
- Package: `com.gba.emulator.shell`

## Build

```bash
cd Project_Android/GbaEmulatorAndroid
./gradlew :app:assembleDebug
```

Open `Project_Android` in Android Studio for composite workspace, or open this folder alone.

## ROM testing

- **SAF:** **Open ROM** on Home (validates GBA header + complement before native load).
- **Workstation smoke:** `GBA_Emulator/tools/run-rom-smoke.ps1` with ROMs in `Project_Android/local/test-roms/` (gitignored).
- **Debug adb push:** `adb push foo.gba /data/data/com.gba.emulator.shell.debug/cache/rom-smoke.gba` then **Load pushed test ROM** (debug APK only).

## Invariants

- No ROM, BIOS, or save assets in-repo or APK.
- `BridgeSelfTest` uses the same synthetic 12-byte ROM as `android_core_bridge_test.cpp`.
- JNI: `GbaCoreBridge` → `android_core_bridge`; `GbaRuntimeBridge` → `AndroidRuntime` (cycle-bounded `step_frame` + RGB565 framebuffer).

## Implemented (dev shell)

- SAF **Open ROM** with header validation, `EmulatorSession`, `GameScreen` loop, `TouchGameControls`, JNI `stop_reason`
- Oboe audio playback, cartridge save + save-state SAF export/import
- Process lifecycle pause on `GameScreen` (`notifyEmulationPaused` + cartridge save flush)
- Debug overlay (frame ms, PC, stop reason), restart, continue-on-abnormal (debug builds)
- `nativeGetCartridgeMetadata` for title/game code/save type after load

## Docs

- `GBA_Emulator/docs/android-integration-plan.md`
- `GBA_Emulator/docs/android-device-soak-checklist.md`
- `GBA_Emulator/docs/controlled-beta-readiness.md`
- `GBA_Emulator/docs/evidence/2026-06-03-android-device-soak-synthetic-pass.md`
- `GBA_Emulator/docs/evidence/rom-smoke-evidence-template.md`
