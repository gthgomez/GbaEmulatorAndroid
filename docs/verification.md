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
ref:        ed73298b81c80371c0d661fe11c18d987f66d20e
```

- Pin exists in the core repo: `ed73298b81c80371c0d661fe11c18d987f66d20e`
  ("fix(ppu): bypass windowing when no window is enabled in DISPCNT (issue #15)").
- Local sibling working tree (`/home/linuxuser/GBA_Emulator`) is on `main`
  at `628d57e`, with unrelated uncommitted edits. **It was left untouched.**
- CI-faithful builds used a separate clone checked out at the pin
  (`/tmp/opencode/gba-core-pinned`) via `GBA_EMULATOR_ROOT`.
- **The pin must not be changed** without a corresponding core-verified change.

### 2026-10-06 pin reconciliation — RESOLVED same day

The deferral below is superseded: the core regression was root-caused to the
renderer rewrite in core `9755c99` (window mask applied while windowing
disabled) and fixed by core PR
[gthgomez/GBA_Emulator#17](https://github.com/gthgomez/GBA_Emulator/pull/17)
(`ed73298`). Core issue
[#15](https://github.com/gthgomez/GBA_Emulator/issues/15) is closed with
verification: frames 0-31 of the Emerald lab run match the pre-regression
core bit-exact, and the 2026-08-14 evidence CRCs reproduce verbatim. The pin
is now `ed73298` per the original re-bump criteria.

### 2026-10-06 pin reconciliation (historical)

Core `main` had advanced to `9f7436a` (20 commits ahead of the pin,
verifier suite 30/30 PASS, 11/13 mGBA suites green). The pin was
**deliberately left at `a36ebb19`**: that revision was the newest core whose
real-game video rendering was verified. A real-game render regression
(Pokemon Emerald rendering uniform black from ~frame 30 while all verifier
suites stayed green) was tracked in core issue
[gthgomez/GBA_Emulator#15](https://github.com/gthgomez/GBA_Emulator/issues/15).

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

## 7. Audio lifecycle serialization correction (2026-10-11)

**Scope:** local (no device attached). Host checks PASS; device audio gates NOT RUN.

`ApuAudioEngine` now serializes every call to the single process-wide native Oboe stream on one
lock, and `GameScreen` stops the stream on `ON_STOP` (instead of clearing the live ring) and
restarts it on `ON_START` (which pre-rolls silence). Two reachable defects this closes:

- `enqueueBatch` runs on `Dispatchers.Default` while `stop()`/`start()` run on the main thread;
  the native holder is a raw pointer that `stop()` deletes, so a concurrent `enqueueBatch` could
  dereference freed memory.
- The Oboe data callback owns the SPSC read cursor; the old `ON_STOP` `clear()` reset both ring
  cursors while the callback could be reading/writing them.

Because `stop()` now destroys the native holder (and its settings), `start()` re-applies the last
requested Steady Music flag and playback-rate multiplier, so pause/resume and restart no longer
silently reset them. `ON_START` now checks the reopen result and logs `audio_resume_stream_failed`
instead of leaving playback silently dead. The now-unused seams were removed: `clear()` from the
Kotlin engine, the native `GbaApuAudioOutput::clear()`, the
`Java_com_gba_emulator_shell_ApuAudioEngine_nativeClear` JNI entry, and the Kotlin
`preRollSilence()` (start() pre-rolls internally).

An independent adversarial review of this diff found no high-severity defect (every JNI entry is
reached only under the lock; no deadlock; the `clear()` removal is complete; the settings
re-application cannot interleave). Its Medium finding — that a failed stream reopen on resume was
ignored — is now handled by the `audio_resume_stream_failed` log above; the reviewer notes the
lock is held across the (main-thread) native stream open/close, which is accepted for this dev
shell since it only occurs on pause/resume.

### Host verification (PASS)

```bash
export ANDROID_HOME=/home/linuxuser/android-sdk
python tools/bootstrap-core.py       # deps/GBA_Emulator @ ed73298 (qualified)
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest --rerun-tasks
```

`BUILD SUCCESSFUL`, 48 tasks executed. Unit tests: **26 passed, 0 failures, 0 errors,
0 skipped** (`ApuAudioEngineTest` 5, `EmulationFramePacerTest` 5, `FramebufferVideoMetricsTest`
7, `PlaybackSpeedControllerTest` 6, `RomValidatorTest` 3). Debug APK
`sha256 6ee56b270e76e13f5ba6242713590979287f325a0afbe70af6482dafbff81025` with both
`arm64-v8a` and `x86_64` `libgbaemulator.so`.

Negative control (shows the new test catches the regression it guards): stripping
`synchronized(lock)` from `ApuAudioEngine` makes
`ApuAudioEngineTest.concurrentLifecycleAndFrameAudioCallsAreSerialized` FAIL (`AssertionError`,
overlapping native calls); restoring the lock passes. The source was restored byte-for-byte.

### Device gates (BLOCKED — no device attached)

- Real Oboe stream open/close latency and audio continuity across pause/resume.
- `playbackUnderruns` behavior across repeated pause/resume and ROM switch.
- `RuntimeSelfTest` audio assertions and the `android-device-soak-checklist.md` soak.

These require a physical device and remain NOT RUN; no device qualification is claimed.
