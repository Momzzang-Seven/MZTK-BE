#!/usr/bin/env python3
"""Create .claude/skills -> .agents/skills link for the current platform.

Idempotent. Run once per fresh clone (or after .claude/skills is removed).
Posix: relative symlink. Windows: directory junction (no Developer Mode required).
"""
from __future__ import annotations

import os
import platform
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
TARGET = REPO_ROOT / ".claude" / "skills"
SOURCE_REL = Path("..") / ".agents" / "skills"
SOURCE_ABS = REPO_ROOT / ".agents" / "skills"


def main() -> int:
    if not SOURCE_ABS.is_dir():
        print(f"error: source not found: {SOURCE_ABS}", file=sys.stderr)
        return 1

    TARGET.parent.mkdir(parents=True, exist_ok=True)

    if TARGET.is_symlink():
        print(f"link already exists: {TARGET}")
        return 0
    if platform.system() == "Windows" and TARGET.exists():
        print(f"junction already exists: {TARGET}")
        return 0
    if TARGET.exists():
        print(
            f"error: {TARGET} exists as a real directory; remove it before running",
            file=sys.stderr,
        )
        return 2

    if platform.system() == "Windows":
        subprocess.run(
            ["cmd", "/c", "mklink", "/J", str(TARGET), str(SOURCE_ABS)],
            check=True,
        )
    else:
        os.symlink(SOURCE_REL, TARGET)
    print(f"linked: {TARGET} -> {SOURCE_REL}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
