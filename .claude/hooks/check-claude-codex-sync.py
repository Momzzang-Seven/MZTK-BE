#!/usr/bin/env python3
"""
PostToolUse hook: enforce docs.shared/AGENT_CONFIG.md sync policy.

Triggers when an agent edits a Claude-side file whose policy says a Codex-side
mirror or counterpart must change too. Surface a reminder via additionalContext
so the agent finishes the work in the same turn — never blocks.

Coverage (informational, never fatal):
  1. .claude/skills/<name>/SKILL.md or .claude/skills/<name>/agents/*.md edited
     → run scripts/agents/sync-skills.py --check; if drift detected, ask the
       agent to re-run sync-skills.py and commit the result.
  2. .claude/settings.json edited
     → remind the agent to mirror permissions/sandbox into .codex/config.toml
       and update docs.shared/AGENT_CONFIG.md if the change is policy-relevant.

All exceptions are swallowed silently so a hook crash never blocks the user.
"""
import sys
import json
import os
import re
import subprocess

SYNC_SCRIPT = "scripts/agents/sync-skills.py"
SKILL_PATH_RE = re.compile(
    r"\.claude/skills/(?P<name>[^/]+)/(SKILL\.md|agents/[^/]+\.md)$"
)
SETTINGS_PATH = ".claude/settings.json"
PERSONAL_SKILLS = {"improve-token-efficiency", "ai-readiness-cartography"}


def _resolve_relative(fp, cwd):
    if fp.startswith(cwd):
        fp = fp[len(cwd):].lstrip("/")
    return fp


def _check_skill_drift():
    """Run sync-skills.py --check; return (is_stale, stdout)."""
    try:
        res = subprocess.run(
            [sys.executable, SYNC_SCRIPT, "--check"],
            capture_output=True,
            text=True,
            timeout=10,
        )
        return res.returncode != 0, res.stdout
    except Exception:
        return False, ""


def _emit(msg):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": msg,
        }
    }))


def main():
    try:
        data = json.load(sys.stdin)
        fp = data.get("tool_input", {}).get("file_path", "")
        if not fp:
            return
        cwd = os.getcwd()
        rel = _resolve_relative(fp, cwd)

        if rel == SETTINGS_PATH:
            msg = (
                ".claude/settings.json 이 수정되었습니다. docs.shared/AGENT_CONFIG.md 의 cross-reference 표에 따라\n"
                "다음 중 해당하는 것이 있는지 확인해 주세요:\n"
                "- permissions / sandbox 변경 → .codex/config.toml 의 [sandbox] 섹션도 동시 갱신\n"
                "- 새 hook 추가 → Codex 는 hook 미지원이므로 AGENT_CONFIG.md 의 미지원 표시 유지\n"
                "- 정책 변경 (skill/permission/hook 운영 규칙) → docs.shared/AGENT_CONFIG.md 표 갱신\n"
                "정책 표 자체에 변경이 없다면 이 알림은 무시해도 됩니다."
            )
            _emit(msg)
            return

        skill_match = SKILL_PATH_RE.search(rel)
        if skill_match:
            name = skill_match.group("name")
            if name in PERSONAL_SKILLS:
                return
            stale, out = _check_skill_drift()
            if not stale:
                return
            msg = (
                f"`.claude/skills/{name}/...` 가 수정되어 Codex mirror 가 stale 합니다.\n"
                "다음을 같은 turn / 같은 PR 안에서 실행해 commit 해 주세요:\n"
                "  python3 scripts/agents/sync-skills.py\n"
                "(SSoT 규칙은 docs.shared/AGENT_CONFIG.md 참조.)\n"
            )
            if out:
                msg += "\n--- sync-skills.py --check 출력 ---\n" + out.strip()
            _emit(msg)
            return
    except Exception:
        # Hook must never block the user
        pass


if __name__ == "__main__":
    main()
