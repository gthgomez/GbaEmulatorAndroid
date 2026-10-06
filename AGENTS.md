# AGENTS.md — GbaEmulatorAndroid

This file is the sole instruction authority for engineering agents working in this
repository. Model- or vendor-specific instruction files (CLAUDE.md, GEMINI.md,
CODEX.md, and similar) are prohibited here; do not create or consult them. Nested
instruction files are also prohibited. Factual and architectural context lives in
`docs/PROJECT_CONTEXT.md` and is task data, not policy.

## What This Is

Android development shell for the sibling `GBA_Emulator` core: Kotlin/Jetpack Compose
UI, SAF ROM loading, SurfaceView game viewport, Oboe audio, and a JNI/CMake bridge.
Dev-shell track, not a store-ready product. See `docs/PROJECT_CONTEXT.md` for the
stack, build commands, and technical background.

## Hard Rules

- **No ROM, BIOS, or save assets in-repo or in APK.** BIOS is HLE'd on `load_rom`
  (`CoreSession::configure_for_game_boot`, entry PC `0x08000000`); no bundled BIOS file.
- **Mutex-safe lifecycle:** all `GbaCoreBridge` / `GbaRuntimeBridge` JNI calls must be
  serialized with a mutex. Never call into the native layer concurrently.
- **Fail-closed cancellation:** if native code returns a stop reason (crash, assertion,
  timeout), the Kotlin layer must surface the error without leaving the emulator in an
  inconsistent state.
- **SAF Open ROM** validates the GBA header + complement before native load.
- **Process lifecycle:** `GameScreen` must call `notifyEmulationPaused` on pause and
  flush cartridge save; audio ring clear + pre-roll on resume.
- **Save states:** versioned codec with deterministic hashes; the hash is checked
  immediately after restore, not after another step.
- **Frame pacing:** targets ~59.727 Hz with direct-select speed chips (1x–4x + Max) and
  Steady Music mode.
- **Never claim a build, test, or device check passed without executed evidence.**

## High-Risk Zones (pause and confirm)

- JNI bridge changes (`GbaCoreBridge.kt`, `GbaRuntimeBridge.kt`)
- CMake configuration (`app/src/main/cpp/CMakeLists.txt`)
- SurfaceView / `GameViewportSurface` rendering path
- Audio ring buffer or Oboe integration
- Process lifecycle handling (pause/resume/background)
- SAF file handling (Open ROM, cartridge save, save-state import/export)
- Any change touching `AndroidManifest.xml`

## Accuracy Cautions

- Do not invent JNI method signatures (name-mangling errors cause
  `UnsatisfiedLinkError`).
- Do not guess `CMakeLists.txt` NDK/ABI configuration (target API, cross-compilation).
- Do not confuse `SurfaceView` lifecycle with Compose — `GameViewportSurface` uses
  `Canvas.drawBitmap`; the Compose `Image` preview on Home is development-only.
- Do not hallucinate Oboe audio API bindings (callback signatures, ring buffer init).
- Handle SAF document URIs exactly; ROM loading requires header validation.

## Rendering and Audio Invariants

- `GameViewportSurface` uses `SurfaceView` + `Canvas.drawBitmap` as the primary
  viewport; the Home-screen Compose `Image` preview is for development only.
- Oboe audio uses an SPSC (single-producer single-consumer) ring buffer with a data
  callback; audio state must be cleared on pause and pre-rolled on resume.
- `BridgeSelfTest` uses the same synthetic 12-byte ROM as
  `android_core_bridge_test.cpp` in `GBA_Emulator`.

## Device Verification

- Device soak: see `GBA_Emulator/docs/android-device-soak-checklist.md` (sibling repo).
- On-device self-tests (`RuntimeSelfTest`, `PersistenceSelfTest`) must mirror C++ step
  budgets from `GBA_Emulator/tests/`.
- ROM smoke testing: `GBA_Emulator/tools/run-rom-smoke.ps1` with out-of-tree ROMs
  (gitignored; no ROM assets in this repository).

## Build and Test

```bash
./gradlew :app:assembleDebug
```

- Device self-test: run `BridgeSelfTest` on device after install.
- Soak testing: `GBA_Emulator/docs/android-device-soak-checklist.md`.
- ROM smoke: `GBA_Emulator/tools/run-rom-smoke.ps1` (requires out-of-tree ROMs).
