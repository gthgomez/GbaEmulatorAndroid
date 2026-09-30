#!/usr/bin/env python3
"""Negative controls for the core bootstrap lock (GA01).

Runs two failure-path checks against tools/bootstrap-core.py:

1. Tampered lock: a modified core commit must make bootstrap fail with a
   clear error (no qualified build/bootstrap against a different core).
2. Path with spaces, no sibling core: bootstrap must succeed from a repo
   copy living in a directory whose name contains spaces, with no
   sibling ../GBA_Emulator present (Windows/Linux path support).

Exit status 0 = all controls passed; nonzero = a control failed.
Requires network access to the pinned public core repository.
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
BOOTSTRAP = REPO_ROOT / "tools" / "bootstrap-core.py"
LOCK_COMMIT_RE = re.compile(r'("commit"\s*:\s*")([0-9a-f]+)(")')

results: list[tuple[str, bool, str]] = []


def rmtree_force(path: Path) -> None:
    """rmtree that clears the read-only attribute git sets on pack files (Windows)."""
    import os
    import stat

    def onerror(func, p, _exc):
        os.chmod(p, stat.S_IWRITE)
        func(p)

    shutil.rmtree(path, onerror=onerror)


def record(name: str, ok: bool, detail: str) -> None:
    results.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}: {detail}")


def run_bootstrap(repo: Path, *extra: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(repo / "tools" / "bootstrap-core.py"), *extra],
        cwd=str(repo), capture_output=True, text=True,
    )


# Control 1: a lock commit that cannot be served must make bootstrap fail
# with a clear error (never fall back to a branch head or an unpinned fetch).
tampered_root = Path(__file__).resolve().parent.parent / "build" / "ga01-negative"
tampered_root.mkdir(parents=True, exist_ok=True)
tampered_repo = tampered_root / "repo tampered lock"
if tampered_repo.exists():
    rmtree_force(tampered_repo)
shutil.copytree(REPO_ROOT, tampered_repo,
                ignore=shutil.ignore_patterns(".git", "deps", "build", ".cxx", ".gradle"))
lock_path = tampered_repo / "core.lock.json"
lock_text = lock_path.read_text(encoding="utf-8")
WRONG_COMMIT = "0" * 40
tampered_text = LOCK_COMMIT_RE.sub(lambda m: m.group(1) + WRONG_COMMIT + m.group(3), lock_text, count=1)
lock_path.write_text(tampered_text, encoding="utf-8")
assert tampered_text != lock_text, "failed to tamper the lock commit"

proc = run_bootstrap(tampered_repo, "--dest", str(tampered_root / "deps tampered"))
record("tampered-lock-fails",
       proc.returncode != 0 and WRONG_COMMIT in (proc.stderr + proc.stdout),
       f"exit={proc.returncode} stderr_tail={proc.stderr.strip().splitlines()[-1] if proc.stderr.strip() else ''!r}")

# Control 1b: a core checkout whose HEAD was mutated (simulating someone
# checking out / amending a different core revision) must fail --verify.
verified_repo = tampered_root / "repo tampered marker"
if verified_repo.exists():
    rmtree_force(verified_repo)
shutil.copytree(REPO_ROOT, verified_repo,
                ignore=shutil.ignore_patterns(".git", "deps", "build", ".cxx", ".gradle"))
proc = run_bootstrap(verified_repo)
core_dir = verified_repo / "deps" / "GBA_Emulator"
if proc.returncode == 0 and (core_dir / ".git").exists():
    amend = subprocess.run(
        ["git", "-c", "user.name=negative-control", "-c", "user.email=nc@example.invalid",
         "commit", "--amend", "-m", "mutated core head", "--no-verify"],
        cwd=str(core_dir), capture_output=True, text=True)
    if amend.returncode == 0:
        proc2 = run_bootstrap(verified_repo, "--verify")
        record("mutated-core-verify-fails",
               proc2.returncode != 0 and "expected locked" in proc2.stderr,
               f"exit={proc2.returncode} stderr_tail={proc2.stderr.strip().splitlines()[-1] if proc2.stderr.strip() else ''!r}")
    else:
        record("mutated-core-verify-fails", False,
               f"skipped: amend failed: {amend.stderr.strip()[:200]!r}")
else:
    record("mutated-core-verify-fails", False,
           f"skipped: initial bootstrap exit={proc.returncode} stderr={proc.stderr.strip()[:200]!r}")

# Control 2: path containing spaces, no sibling core, must succeed.
space_root = tampered_root / "path with spaces"
space_repo = space_root / "GbaEmulatorAndroid"
if space_repo.exists():
    rmtree_force(space_repo)
shutil.copytree(REPO_ROOT, space_repo,
                ignore=shutil.ignore_patterns(".git", "deps", "build", ".cxx", ".gradle"))
proc = run_bootstrap(space_repo)
ok = proc.returncode == 0 and "deps" in proc.stdout and "GBA_Emulator" in proc.stdout
record("spaces-path-succeeds", ok,
       f"exit={proc.returncode} stdout={proc.stdout.strip()!r} stderr={proc.stderr.strip()[:200]!r}")

failed = [name for name, ok, _ in results if not ok]
print()
print(f"ga01 negative controls: {len(results) - len(failed)}/{len(results)} passed")
if failed:
    print(f"FAILED controls: {', '.join(failed)}")
sys.exit(1 if failed else 0)
