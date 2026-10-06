# GbaEmulatorAndroid

Android development shell for the GBA_Emulator core. Provides a Compose UI with SAF ROM loading, SurfaceView game viewport, Oboe audio, and save state management via a JNI/CMake bridge.

**Tech stack:** Kotlin, Jetpack Compose, NDK CMake (compiles the pinned `GBA_Emulator` core), Oboe audio, SurfaceView, SAF.

**Core bootstrap (required for a qualified build):** the core pin lives in [core.lock.json](core.lock.json). Fetch and verify it locally (works on Windows and Linux; the checkout goes to gitignored `deps/GBA_Emulator`):

```sh
python tools/bootstrap-core.py
```

The command prints the verified core path; CI uses the same lock, so local and CI build against the same core revision. CMake consumes the bootstrapped path automatically. Development overrides remain available for local hacking — set `GBA_CORE_OVERRIDE` (or rely on the sibling `../GBA_Emulator` layout) — but CMake will report them as **NOT QUALIFIED** against the lock, and a bootstrapped build fails fast if the core on disk does not match the locked SHA.

**Build:** `.\gradlew.bat :app:assembleDebug`

**Project docs:** [STATUS.md](STATUS.md) | [QA_CHECKLIST.md](QA_CHECKLIST.md) | [docs/verification.md](docs/verification.md)

**Agent instructions:** [AGENTS.md](AGENTS.md); technical context in [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) (task data).
