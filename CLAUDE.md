# CLAUDE.md — GbaEmulatorAndroid

## Model & Trust Configuration

- **Primary model:** Sonnet 4.6
- **Trust level:** High autonomy for UI/shell changes; pause for JNI, CMake, or lifecycle changes

## Context Stack

1. Read this file (`GbaEmulatorAndroid/CLAUDE.md`)
2. Read `GbaEmulatorAndroid/PROJECT_CONTEXT.md`
3. Read root `PROJECT_CONTEXT.md` for workspace context
4. Read root `CLAUDE.md` (`Project_Android/CLAUDE.md`) for behavioral rules and Kotlin/Android patterns
5. Review `tasks/lessons.md` if it exists

## JNI Invariants

- **Mutex-safe lifecycle:** All `GbaCoreBridge` / `GbaRuntimeBridge` JNI calls must be serialized with a mutex. Never call into the native layer concurrently.
- **Fail-closed cancellation:** If native code returns a stop reason (crash, assertion, timeout), the Kotlin layer must surface the error without leaving the emulator in an inconsistent state.
- **SurfaceView framebuffer:** The `GameViewportSurface` uses `SurfaceView` + `Canvas.drawBitmap` for the primary viewport. The Compose `Image` preview on the Home screen is for development only.
- **Oboe audio ring buffer:** Uses SPSC (single-producer single-consumer) with a data callback. Audio state must be cleared on pause and pre-rolled on resume.
- **Bridge self-test:** `BridgeSelfTest` uses the same synthetic 12-byte ROM as `android_core_bridge_test.cpp` in `GBA_Emulator`.

## Device Verification

- See `GBA_Emulator/docs/android-device-soak-checklist.md` for device-level soak testing requirements.
- Runtime self-tests (`RuntimeSelfTest`, `PersistenceSelfTest`) must mirror C++ step budgets from `GBA_Emulator/tests/`.
- ROM smoke testing: `GBA_Emulator/tools/run-rom-smoke.ps1` with ROMs in `Project_Android/local/test-roms/` (gitignored).

## Invariants

- **No ROM, BIOS, or save assets in-repo or in APK.** BIOS HLE is used on `load_rom` — no bundled BIOS file.
- **SAF Open ROM** validates GBA header + complement before native load.
- **Emulation frame pacing** targets ~59.727 Hz with direct-select speed chips (1x-4x + Max) and Steady Music mode.
- **Process lifecycle:** `GameScreen` must call `notifyEmulationPaused` on pause and flush cartridge save. Audio ring clear + pre-roll on resume.
- **Save states:** Versioned codec with deterministic hashes. Save-state hash is checked immediately after restore.

## High-Risk Zones (Pause and Confirm)

- JNI bridge changes (`GbaCoreBridge.kt`, `GbaRuntimeBridge.kt`)
- CMake configuration (`app/src/main/cpp/CMakeLists.txt`)
- SurfaceView / GameViewportSurface rendering path
- Audio ring buffer or Oboe integration
- Process lifecycle handling (pause/resume/background)
- SAF file handling (Open ROM, cartridge save, save-state import/export)
- Any change touching `AndroidManifest.xml`

## Build

```bash
cd Project_Android/GbaEmulatorAndroid
./gradlew :app:assembleDebug
```

## Testing

- Device self-test: Run `BridgeSelfTest` on device after install
- Soak testing: See `GBA_Emulator/docs/android-device-soak-checklist.md`
- ROM smoke: `GBA_Emulator/tools/run-rom-smoke.ps1` (requires out-of-tree ROMs)
