# Security Policy

## Scope

`GbaEmulatorAndroid` is the Android development shell for the `GBA_Emulator` core. It loads ROM
files chosen through the Storage Access Framework, passes them over a JNI/CMake bridge into the
native emulator, and renders video, audio, and save state on device.

A ROM selected through SAF is untrusted input from an arbitrary third-party app or a downloaded
file, so the risk surface here is: untrusted binary data reaching native code, plus the storage,
IPC, and file-permission boundaries of the Android host.

In scope: the Kotlin app under `app/`, the NDK/CMake bridge, manifest permissions and exported
components, save-state serialization and restore, and the handling of ROM paths obtained via SAF.

Out of scope: the emulator core itself, which is maintained in
[`GBA_Emulator`](../GBA_Emulator) and reported there, and any vulnerability that requires an
already-rooted or otherwise compromised device.

## Supported Versions

This project is pre-1.0 and is not API-stable.

| Version | Supported |
| --- | --- |
| `main` (latest commit) | Yes |
| Latest tagged release | Yes |
| Any earlier commit, branch, or release | No |

Only the current tip of `main` and the most recent release receive security fixes.

## Reporting a Vulnerability

Use GitHub's private vulnerability reporting: go to the repository's **Security** tab and click
**Report a vulnerability**. This opens a private advisory visible only to the maintainer.

If private reporting is unavailable to your account, open a
[security advisory](https://github.com/gthgomez/GbaEmulatorAndroid/security/advisories/new)
directly. There is no published email address for this project, so the advisory channel is the
supported route.

Please do not open a public issue for an unfixed defect, and do not attach a copyrighted ROM to a
report.

## What to Include

- Type of defect: native memory-safety crash reachable from a crafted ROM or save state, path or
  permission-handling flaw in the SAF flow, save-state deserialization issue, component exported
  beyond intent, or a data-handling issue covered by `PRIVACY.md`.
- Affected commit SHA or release tag, device and Android version, ABI (`arm64-v8a` or otherwise),
  and whether the build was debug or release.
- Steps to load the input, or a small homebrew ROM that triggers the issue.
- Crash output or logcat excerpt.
- Whether the app was installed from source on a non-rooted device.

## Maintainer Response

The maintainer commits to the following:

- Acknowledge a report within 7 days.
- Provide a severity assessment and a remediation or mitigation plan within 30 days of
  acknowledgement.
- Credit reporters in the advisory and release notes unless anonymity is requested.

Fixes are published on `main` first, then folded into the next release.

## Coordinated Disclosure

Fixes land before public disclosure. A reporter should allow up to 90 days from first contact for
a fix or a documented mitigation before publishing, and the maintainer will not cut that period
short without agreeing with the reporter.

## No Bug Bounty

There is no bug bounty program for this project, and no payment is offered for reports. Credit and
a public advisory are the entire compensation.
