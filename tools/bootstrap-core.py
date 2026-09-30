#!/usr/bin/env python3
"""Bootstrap the GBA_Emulator core pinned in core.lock.json.

Fetches the exact pinned public revision into a repo-local, gitignored
dependency directory, validates the checked-out SHA against the lock, writes
a verification marker consumed by CMake, and prints the verified local path
on stdout (the only stdout output, so CI can capture it directly).

The default sibling-checkout layout and the GBA_CORE_OVERRIDE environment
variable remain supported as *development overrides*: they are reported as
NOT QUALIFIED on stderr and never used by CI or qualified builds.

Windows and Linux path conventions are supported (pathlib throughout).

Usage:
  python tools/bootstrap-core.py [--verify] [--force] [--lock PATH] [--dest PATH]

  --verify   Validate an existing bootstrapped checkout against the lock
             without fetching. Fails with a clear error if absent or mutated.
  --force    Re-fetch even if the destination already verifies.
  (default)  Fetch the pinned commit if needed, verify, print the local path.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LOCK = REPO_ROOT / "core.lock.json"
MARKER_NAME = ".core-lock-verified"


def fail(msg: str) -> "NoReturn":  # type: ignore[valid-type]
    print(f"bootstrap-core: ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def warn(msg: str) -> None:
    print(f"bootstrap-core: WARNING: {msg}", file=sys.stderr)


def load_lock(lock_path: Path) -> tuple[str, str, str]:
    try:
        lock = json.loads(lock_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read core lock {lock_path}: {exc}")
    core = lock.get("core", {})
    repository = core.get("repository")
    commit = core.get("commit")
    if not repository or not commit:
        fail(f"core lock {lock_path} is missing core.repository or core.commit")
    marker_name = lock.get("bootstrap", {}).get("verification_marker", MARKER_NAME)
    return repository, commit, marker_name


def git(*args: str, cwd: Path | None = None) -> str:
    result = subprocess.run(
        ["git", *args], cwd=str(cwd) if cwd else None,
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        fail(f"git {' '.join(args)} failed:\n{result.stderr.strip()}")
    return result.stdout.strip()


def head_commit(core_dir: Path) -> str:
    out = git("rev-parse", "HEAD", cwd=core_dir)
    return out.strip()


def fetch_pinned(repository: str, commit: str, dest: Path) -> None:
    """Clone/fetch the exact pinned commit. Never fetches a branch head."""
    if dest.exists():
        git("fetch", "--quiet", repository, commit, cwd=dest)
    else:
        dest.parent.mkdir(parents=True, exist_ok=True)
        # Full clone so any pinned historical SHA is reachable; no branch is
        # ever fetched or checked out by name.
        git("clone", "--quiet", "--no-checkout", repository, str(dest))
        git("fetch", "--quiet", repository, commit, cwd=dest)
    git("checkout", "--quiet", "--detach", commit, cwd=dest)


def write_marker(core_dir: Path, commit: str, marker_name: str) -> None:
    (core_dir / marker_name).write_text(commit + "\n", encoding="utf-8")


def read_marker(core_dir: Path, marker_name: str) -> str | None:
    marker = core_dir / marker_name
    if not marker.is_file():
        return None
    return marker.read_text(encoding="utf-8").strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK,
                        help="path to core.lock.json")
    parser.add_argument("--dest", type=Path,
                        default=REPO_ROOT / "deps" / "GBA_Emulator",
                        help="dependency directory for the core checkout")
    parser.add_argument("--verify", action="store_true",
                        help="validate an existing checkout; do not fetch")
    parser.add_argument("--force", action="store_true",
                        help="re-fetch even if the checkout already verifies")
    args = parser.parse_args()

    # Development override: an explicit GBA_CORE_OVERRIDE points the build at
    # an arbitrary local checkout. This is a convenience for local hacking;
    # it bypasses lock validation and is NOT QUALIFIED for evidence purposes.
    override = None
    if not args.verify and not args.force:
        override = __import__("os").environ.get("GBA_CORE_OVERRIDE")
    if override:
        override_path = Path(override).resolve()
        if not (override_path / "include" / "gba" / "core" / "android_core_bridge.hpp").is_file():
            fail(f"GBA_CORE_OVERRIDE={override_path} does not contain the GBA core bridge header")
        warn(f"using GBA_CORE_OVERRIDE={override_path} — development override, NOT QUALIFIED "
             f"(does not match core.lock.json {args.lock})")
        print(override_path)
        return

    repository, commit, marker_name = load_lock(args.lock)
    dest = args.dest.resolve()

    if dest.exists() and not args.force:
        try:
            current = head_commit(dest)
        except SystemExit:
            fail(f"{dest} exists but is not a usable git checkout; remove it or pass --force")
        if current == commit:
            # HEAD is authoritative; re-stamp the verification marker (e.g.
            # after a previous check tampered with it).
            write_marker(dest, commit, marker_name)
            print(dest)
            return
        if args.verify:
            fail(f"core checkout at {dest} is at {current}, expected locked {commit} "
                 f"from {args.lock}")
        warn(f"{dest} is at {current}, does not match locked {commit}; re-fetching")

    if args.verify:
        fail(f"no verified core checkout at {dest}; run tools/bootstrap-core.py without --verify")

    fetch_pinned(repository, commit, dest)
    current = head_commit(dest)
    if current != commit:
        fail(f"fetched core HEAD {current} does not match locked {commit}")
    write_marker(dest, commit, marker_name)
    print(dest)


if __name__ == "__main__":
    main()
