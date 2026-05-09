#!/usr/bin/env python3
"""
PostToolUse hook: ensure .claude/skills points to .agents/skills.

`.agents/skills/` is the single source of truth for both Claude Code and
Codex CLI. Claude Code only auto-discovers `.claude/skills/`, so each
environment links the two via `scripts/agents/setup-skill-links.py`
(POSIX symlink or Windows directory junction).

This hook fires on Write/Edit/MultiEdit and surfaces an `additionalContext`
reminder when the link is missing or broken. It never blocks — exceptions
are swallowed so a misbehaving hook does not stall the user.
"""
import json
import os
import sys

LINK_PATH = ".claude/skills"
SOURCE_PATH = ".agents/skills"
SETUP_HINT = "python3 scripts/agents/setup-skill-links.py"


def _emit(msg):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": msg,
        }
    }))


def main():
    try:
        # Drain stdin so the harness does not block waiting on us.
        try:
            json.load(sys.stdin)
        except Exception:
            pass

        # Only meaningful from the repo root (where the hook is configured).
        if not os.path.isdir(SOURCE_PATH):
            return

        if not os.path.lexists(LINK_PATH):
            _emit(
                f"`{LINK_PATH}` 가 존재하지 않습니다. Claude Code 가 skills 를 "
                f"인식하려면 `{SOURCE_PATH}` 로의 link 가 필요합니다.\n"
                f"다음을 한 번 실행해 주세요: `{SETUP_HINT}`"
            )
            return

        try:
            resolved = os.path.realpath(LINK_PATH)
        except OSError:
            resolved = ""
        expected = os.path.realpath(SOURCE_PATH)

        if resolved != expected or not os.path.isdir(resolved):
            _emit(
                f"`{LINK_PATH}` 가 `{SOURCE_PATH}` 를 가리키지 않거나 깨져 "
                f"있습니다.\n"
                f"다음을 실행해 link 를 다시 만들어 주세요: `{SETUP_HINT}`"
            )
    except Exception:
        # Hook must never block the user.
        pass


if __name__ == "__main__":
    main()
