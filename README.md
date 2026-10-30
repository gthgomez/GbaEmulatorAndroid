# GbaEmulatorAndroid

Android development shell for the GBA_Emulator core. Provides a Compose UI with SAF ROM loading, SurfaceView game viewport, Oboe audio, and save state management via a JNI/CMake bridge.

## Showcase — Open a legal test ROM on Android

Load caller-provided ROM data through Android’s system picker, play in the SurfaceView viewport, and manage emulator save states. This is the Android development shell for the separately maintained [GBA_Emulator](https://github.com/gthgomez/GBA_Emulator) core; it is not an independent emulator engine.

| Explore | What the app provides |
| --- | --- |
| Open ROM | Android Storage Access Framework and header validation |
| Game view | SurfaceView viewport, controls, and speed selection |
| Audio and saves | Oboe output and core-backed persistence; device verification required |

**Demo status:** runtime screenshots and video are pending. The README showcase pass
on 2026-10-30 inspected source at [`93692882a7b8`](https://github.com/gthgomez/GbaEmulatorAndroid/tree/93692882a7b869fbfabb51e051ff7e51111677a8);
this cloud environment has no Android SDK, emulator, or attached Android device.
See the [capture checklist and evidence limits](docs/showcase/README.md).

### Try it locally

JDK 17, Android SDK platform 36, NDK 28.2.13676358, CMake 3.22.1, and an Android 8.0+ device or supported emulator. Bootstrap the pinned core before building.

From the repository root (Windows: `gradlew.bat`):

```sh
python3 tools/bootstrap-core.py
./gradlew :app:assembleDebug
```

**Tech stack:** Kotlin, Jetpack Compose, NDK CMake (compiles the pinned `GBA_Emulator` core), Oboe audio, SurfaceView, SAF.

**Core bootstrap (required for a qualified build):** the core pin lives in [core.lock.json](core.lock.json). Fetch and verify it locally (works on Windows and Linux; the checkout goes to gitignored `deps/GBA_Emulator`):

```sh
python tools/bootstrap-core.py
```

The command prints the verified core path; CI uses the same lock, so local and CI build against the same core revision. CMake consumes the bootstrapped path automatically. Development overrides remain available for local hacking — set `GBA_CORE_OVERRIDE` (or rely on the sibling `../GBA_Emulator` layout) — but CMake will report them as **NOT QUALIFIED** against the lock, and a bootstrapped build fails fast if the core on disk does not match the locked SHA.

**Build:** `.\gradlew.bat :app:assembleDebug`

**Project docs:** [STATUS.md](STATUS.md) | [QA_CHECKLIST.md](QA_CHECKLIST.md) | [docs/verification.md](docs/verification.md)

**Agent instructions:** [AGENTS.md](AGENTS.md); technical context in [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) (task data).
