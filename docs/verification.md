# Verification Evidence — JVM Unit Tests + DEBUG Guards

**Date:** 2026-10-06
**Scope:** local (no device attached)
**Status:** JVM unit tests and `assembleDebug` PASS; device checks NOT RUN.

This file records what was verified locally, how to reproduce it, and which
device-only checks remain outstanding. It is not a substitute for the device
soak checklist in `GBA_Emulator/docs/android-device-soak-checklist.md`.

## 1. Pinned sibling core

The app builds against the sibling `GBA_Emulator` C++ core. The CI workflow
(`.github/workflows/ci.yml`) pins an exact SHA for reproducible builds:

```
repository: gthgomez/GBA_Emulator
ref:        a36ebb198db607230936eb3c7417674b7b62fab6
```

- Pin exists in the core repo: `a36ebb198db607230936eb3c7417674b7b62fab6`
  ("Merge pull request #3 from gthgomez/fix/state-hash-hotpath").
- Local sibling working tree (`/home/linuxuser/GBA_Emulator`) is on `main`
  at `628d57e`, with unrelated uncommitted edits. **It was left untouched.**
- CI-faithful builds used a separate clone checked out at the pin
  (`/tmp/opencode/gba-core-pinned`) via `GBA_EMULATOR_ROOT`.
- **The pin must not be changed** without a corresponding core-verified change.

### 2026-10-06 pin reconciliation

Core `main` has since advanced to `9f7436a` (20 commits ahead of the pin,
verifier suite 30/30 PASS, 11/13 mGBA suites green). The pin is **deliberately
left at `a36ebb19`**: that revision is the newest core whose real-game video
rendering is verified. Core commits `6785fcc`..`9f7436a` (hardware-timing
Phase 1) introduced a real-game render regression — Pokemon Emerald renders
uniform black from ~frame 30 while all verifier suites stay green — bisected
and documented in core repo issue
[gthgomez/GBA_Emulator#15](https://github.com/gthgomez/GBA_Emulator/issues/15)
(evidence: `docs/evidence/2026-10-06-emerald-video-regression-bisect.md`).

Re-bump the pin to the then-current core `main` only after that issue is
closed and the desktop lab (`gba-desktop --headless`) confirms restored
real-game framebuffer content.

## 2. JVM unit tests (fresh, not from cache)

Run with the test task forced to re-execute (`--rerun`) so the result is not a
Gradle build-cache hit:

```bash
cd GbaEmulatorAndroid
export ANDROID_HOME=/home/linuxuser/android-sdk
export GBA_EMULATOR_ROOT=/tmp/opencode/gba-core-pinned   # pinned core
./gradlew :app:testDebugUnitTest --rerun --console=plain
```

Result: `:app:testDebugUnitTest` executed, `BUILD SUCCESSFUL`.

| Suite | Tests |
| --- | --- |
| `PlaybackSpeedControllerTest` | 6 |
| `RomValidatorTest` | 3 |
| `EmulationFramePacerTest` | 5 |
| `FramebufferVideoMetricsTest` | 7 |
| **Total** | **21 passed, 0 failures, 0 errors, 0 skipped** |

The suite grew from 13 to 21 with the additions in section 5. XML results live
under `app/build/test-results/testDebugUnitTest/`.

## 3. Build verification (pinned core)

```bash
export GBA_EMULATOR_ROOT=/tmp/opencode/gba-core-pinned
./gradlew :app:assembleDebug --console=plain
```

Result: `BUILD SUCCESSFUL`; APK produced with both ABIs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `sha256 1f7593f0d2fa5c4226febc1a29ff6ef85da86114c4f9196111caf2d6979017bb`
- `lib/arm64-v8a/libgbaemulator.so`, `lib/x86_64/libgbaemulator.so`
  (plus `libc++_shared.so`)

This mirrors the CI job `./gradlew :app:assembleDebug :app:testDebugUnitTest`.

## 4. QuickBoot / DevTools auto-ROM DEBUG guards

Static verification (no other `PushedRomLoader` call sites exist; the manifest
has no intent filters or content providers):

| Path | Location | Guard |
| --- | --- | --- |
| Auto-load pushed ROM on launch | `MainActivity.kt:58` | `if (BuildConfig.DEBUG && PushedRomLoader.hasPushedRom(context))` |
| Quick Boot (Debug) card / Load pushed ROM | `ui/GbaEmulatorScreen.kt:692` | `if (BuildConfig.DEBUG && !running)` |
| Dev Tools panel (bridge / persistence / ROM-video / runtime self-tests) | `ui/GbaEmulatorScreen.kt:773` | `if (BuildConfig.DEBUG)` |
| ROM video self-test (reads `PushedRomLoader`) | `RomVideoSelfTest.kt:24` | reachable only from the Dev Tools panel above |

Generated `BuildConfig` for each build type:

- debug:   `public static final boolean DEBUG = Boolean.parseBoolean("true");`
- release: `public static final boolean DEBUG = false;`

Conclusion: the auto-ROM paths are **runtime-unreachable in release**. Note the
release build has `isMinifyEnabled = false`, so this is a dead branch, not
compile-time code removal; the guard is still the standard, sufficient Android
pattern. A release *leak* (an unguarded reachable path) was not found.

## 5. Tests added

- `app/src/test/java/com/gba/emulator/shell/FramebufferVideoMetricsTest.kt`
  (7 tests) — pure RGB565 stats used by the device `RomVideoSelfTest` gate:
  unique-color counting, dominant ratio, CRC determinism, wrong-size rejection,
  and uniform-backdrop decisions.
- `EmulationFramePacerTest.longStallIsCappedAtMaxCatchupSlots` — a multi-second
  stall is capped at 4 slots (bounded catch-up), guarding the frame-pacing path.

These run on the JVM and do not load the native library.

## 6. Device checks — NOT RUN

No Android device/emulator was attached, so **no device qualification is
claimed**. There is no `androidTest` source set; audio, lifecycle, and soak are
manual on-device checks only.

| Area | Local JVM coverage | Device status |
| --- | --- | --- |
| Frame pacing | `EmulationFramePacerTest`, `PlaybackSpeedControllerTest` | NOT RUN (120 Hz overlay, real frame ms) |
| ROM video gate | `FramebufferVideoMetricsTest` (analysis only) | NOT RUN (`RomVideoSelfTest`, needs pushed ROM) |
| Audio | none (JNI/device only) | NOT RUN (`RuntimeSelfTest` audio assertions; Oboe stream) |
| Lifecycle (pause/resume/Home) | none | NOT RUN |
| Soak (≥60 s, leak trend) | none | NOT RUN |

Execute `GBA_Emulator/docs/android-device-soak-checklist.md` on a physical
device and file the dated artifact before claiming controlled-beta readiness.
