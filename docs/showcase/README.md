# Showcase capture status

Source inspected: [`93692882a7b869fbfabb51e051ff7e51111677a8`](https://github.com/gthgomez/GbaEmulatorAndroid/tree/93692882a7b869fbfabb51e051ff7e51111677a8).
Inspection date: 2026-10-30. This is a source/documentation pass, not a recorded app run.

## Evidence available

No verified runtime screenshots or clips are checked into this repository at the
inspected revision. No new runtime media was created for this pass.

## Execution blocker

The selected cloud environment has JDK 21 but no Android SDK installation,
`ANDROID_HOME`/`ANDROID_SDK_ROOT`, `adb`, `emulator`, `sdkmanager`, `/dev/kvm`,
or attached Android device. This repository’s existing JDK 17 setup and Android
prerequisites were inspected rather than replaced with a large toolchain download.

Required next setup: JDK 17, Android SDK platform 36, NDK 28.2.13676358, CMake 3.22.1, and an Android 8.0+ device or supported emulator. Bootstrap the pinned core before building.

No Android build, bridge check, audio check, or device soak was executed for this showcase.

## Capture a genuine demonstration

On a test device, open a legally obtained mGBA test-suite ROM from outside the repository, exercise the controls, then pause/resume and capture a viewport. Run the bridge self-test and the sibling core’s device soak checklist separately. Never commit a ROM, BIOS, cartridge save, or save state.

After installing and manually opening the debug build on the test device, record
screens using Android's actual display output (requires Android platform tools):

```sh
mkdir -p docs/showcase
adb exec-out screencap -p > docs/showcase/screen.png
# Stop recording with Ctrl-C after the short walkthrough.
adb shell screenrecord --time-limit 20 /sdcard/showcase.mp4
adb pull /sdcard/showcase.mp4 docs/showcase/demo.mp4
adb shell rm /sdcard/showcase.mp4
```

Inspect every frame before publication. Keep the clip short and compress it for
GitHub; do not add large model/APK/build/ROM/save files. Add captions identifying
source commit, build variant, device/Android version, fixture origin, and which
steps were really exercised. Scripted UI states and design references must stay
labeled separately from device and end-to-end evidence.

## Source and verification references

[Core lock](../../core.lock.json), [verification](../verification.md), and [project context](../PROJECT_CONTEXT.md).

This pass changes README/capture documentation only. Local paths and links are
checked; Android Gradle, connected, release, and hardware gates remain unrun.
