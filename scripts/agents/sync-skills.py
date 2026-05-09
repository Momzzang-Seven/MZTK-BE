#!/usr/bin/env python3
"""
sync-skills.py — Single source of truth: `.claude/skills/<name>/SKILL.md`.

Generates one-way mirror for Codex CLI:
  .codex/prompts/<name>.md

Modes:
  (no flag)  Write mirrors to disk (standard sync)
  --check    Verify mirrors match source without writing; exit 1 + report on drift

Run after editing any skill SKILL.md:
  python3 scripts/agents/sync-skills.py

Personal-only skills (gitignored, NOT mirrored):
  improve-token-efficiency, ai-readiness-cartography

Multi-agent skills (multi-agent-review): sub-agent files under <skill>/agents/
are NOT flattened — generated mirror points readers back to the source folder.

External deps: 0 (Python 3 standard library only).
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CLAUDE_SKILLS = REPO_ROOT / ".claude" / "skills"
CODEX_PROMPTS = REPO_ROOT / ".codex" / "prompts"

PERSONAL_SKILLS = {"improve-token-efficiency", "ai-readiness-cartography"}

GENERATED_BANNER = (
    "<!-- GENERATED FROM .claude/skills/{name}/SKILL.md by scripts/agents/sync-skills.py.\n"
    "     DO NOT EDIT DIRECTLY — edit the source SKILL.md and re-run the script. -->\n\n"
)

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n(.*)$", re.DOTALL)


def parse_frontmatter(text: str) -> tuple[dict[str, str], str]:
    """Minimal YAML-like frontmatter parser (key: value only, supports `>` block scalar)."""
    m = FRONTMATTER_RE.match(text)
    if not m:
        return {}, text
    fm_raw, body = m.group(1), m.group(2)
    fields: dict[str, str] = {}
    current_key: str | None = None
    current_lines: list[str] = []
    is_block: bool = False

    def flush():
        nonlocal current_key, current_lines, is_block
        if current_key is None:
            return
        if is_block:
            joined = " ".join(line.strip() for line in current_lines if line.strip())
            fields[current_key] = joined
        else:
            fields[current_key] = " ".join(current_lines).strip()
        current_key = None
        current_lines = []
        is_block = False

    for line in fm_raw.splitlines():
        if not line.strip():
            continue
        # Top-level key (not indented)
        if not line.startswith(" ") and ":" in line:
            flush()
            key, _, val = line.partition(":")
            key = key.strip()
            val = val.strip()
            if val == ">" or val == ">-" or val == "|":
                current_key = key
                is_block = True
                current_lines = []
            else:
                # single-line scalar, possibly quoted
                v = val.strip().strip('"').strip("'")
                fields[key] = v
        else:
            # continuation (indented or block-scalar body)
            if current_key is not None:
                current_lines.append(line)
    flush()
    return fields, body


def yaml_quote(value: str) -> str:
    """Quote a value safely for YAML frontmatter."""
    if not value:
        return '""'
    needs_quote = any(c in value for c in ":#'\"\n") or value.startswith(("-", "?", "*"))
    if needs_quote:
        escaped = value.replace("\\", "\\\\").replace('"', '\\"')
        return f'"{escaped}"'
    return value


def render_codex(name: str, fields: dict[str, str], body: str, has_subagents: bool) -> str:
    """Return the canonical Codex mirror content for a given source skill (no I/O)."""
    description = fields.get("description", "")
    parts = [
        "---\n",
        f"name: {yaml_quote(name)}\n",
        f"description: {yaml_quote(description)}\n",
        "---\n",
        "\n",
        GENERATED_BANNER.format(name=name),
    ]
    if has_subagents:
        parts.append(
            f"> NOTE: This skill defines sub-agents under "
            f"`.claude/skills/{name}/agents/`. Codex CLI does not yet support "
            "multi-agent dispatch; treat this as a single-pass prompt. For full "
            "multi-agent execution, run via Claude Code.\n\n"
        )
    parts.append(body.lstrip("\n"))
    return "".join(parts)


def write_codex(name: str, content: str) -> None:
    out_path = CODEX_PROMPTS / f"{name}.md"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(content, encoding="utf-8")


def cleanup_stale_mirrors(shared_skills: set[str], *, dry_run: bool = False) -> list[Path]:
    """Remove mirrors for skills no longer present (or now personal). Returns list."""
    removed: list[Path] = []
    if not CODEX_PROMPTS.exists():
        return removed
    for f in CODEX_PROMPTS.iterdir():
        if not f.is_file() or f.suffix != ".md":
            continue
        if f.stem not in shared_skills:
            removed.append(f)
            if not dry_run:
                f.unlink()
                print(f"  removed stale mirror: {f.relative_to(REPO_ROOT)}")
    return removed


def collect_sources() -> dict[str, dict[str, object]]:
    """Map skill_name → {fields, body, has_subagents, codex_content}. Skips personal."""
    out: dict[str, dict[str, object]] = {}
    if not CLAUDE_SKILLS.is_dir():
        return out
    for entry in sorted(CLAUDE_SKILLS.iterdir()):
        if not entry.is_dir():
            continue
        name = entry.name
        if name in PERSONAL_SKILLS:
            continue
        skill_md = entry / "SKILL.md"
        if not skill_md.is_file():
            continue
        text = skill_md.read_text(encoding="utf-8")
        fields, body = parse_frontmatter(text)
        if "name" not in fields:
            fields["name"] = name
        has_subagents = (entry / "agents").is_dir()
        out[name] = {
            "fields": fields,
            "body": body,
            "has_subagents": has_subagents,
            "codex_content": render_codex(name, fields, body, has_subagents),
        }
    return out


def run_check() -> int:
    """Compare on-disk mirrors against in-memory render. Exit 1 with summary on drift."""
    sources = collect_sources()
    drift: list[tuple[str, str]] = []  # (kind, name)
    for name, src in sources.items():
        target = CODEX_PROMPTS / f"{name}.md"
        expected = src["codex_content"]
        if not target.is_file():
            drift.append(("missing", name))
            continue
        actual = target.read_text(encoding="utf-8")
        if actual != expected:
            drift.append(("stale", name))
    # Stale removals (mirrors that no longer have a source)
    stale = cleanup_stale_mirrors(set(sources.keys()), dry_run=True)
    for f in stale:
        drift.append(("orphan", f.stem))

    if not drift:
        return 0

    print("Codex mirror drift detected:")
    for kind, name in drift:
        print(f"  - {kind}: {name}")
    print()
    print("Fix: python3 scripts/agents/sync-skills.py")
    return 1


def main(argv: list[str]) -> int:
    if "--check" in argv:
        return run_check()

    if not CLAUDE_SKILLS.is_dir():
        print(f"error: {CLAUDE_SKILLS} not found", file=sys.stderr)
        return 1

    sources = collect_sources()
    for name, src in sources.items():
        write_codex(name, src["codex_content"])
        suffix = " (with sub-agents)" if src["has_subagents"] else ""
        print(f"  synced: {name}{suffix}")

    cleanup_stale_mirrors(set(sources.keys()))
    print(f"\nDone. {len(sources)} skill(s) synced to .codex/prompts/.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
