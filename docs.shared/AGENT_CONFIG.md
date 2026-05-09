# AGENT_CONFIG.md — AI 도구 간 config / skill 동기화 정책

이 프로젝트는 **Claude Code, Codex CLI** 사용자가 공존한다.
설정과 skill 의 분산을 막기 위해 다음 규칙을 따른다.

---

## 단일 진실 원천 (SSoT)

| 항목 | SSoT 위치 | 다른 도구 동기화 방식 |
|------|----------|---------------------|
| Project instructions | `AGENTS.md` (모든 scope) | 자동 — Claude Code, Codex CLI 모두 직접 읽음 |
| Skills / Custom commands | `.claude/skills/<name>/SKILL.md` | **단방향 sync** — `scripts/agents/sync-skills.py` 가 `.codex/prompts/<name>.md` 자동 생성 |
| Project permissions | `.claude/settings.json` | **수동** — `.codex/config.toml` `[sandbox]` 양쪽 동시 갱신 |
| PostToolUse hooks | `.claude/settings.json` `hooks.PostToolUse` | (Codex 미지원) — Claude 한쪽만 |

`AGENTS.md` 가 없으면 도구가 컨텍스트를 못 읽고, `.claude/skills/` 가 없으면 sync 가 실패한다 — 두 위치는 PR1 이후 항상 존재한다.

---

## 자동 강제 (PostToolUse hook)

`docs.shared/AGENT_CONFIG.md` 는 정책 문서일 뿐이라 사람이나 AI 가 무시할 수 있다. 이를 보완하기 위해 `.claude/hooks/check-claude-codex-sync.py` 가 PostToolUse 시점에 다음을 자동 검사한다 (informational, 차단 아님):

- `.claude/skills/<name>/SKILL.md` 또는 `.claude/skills/<name>/agents/*.md` 편집 시 → `sync-skills.py --check` 결과가 stale 이면 즉시 sync 재실행 안내
- `.claude/settings.json` 편집 시 → permissions/sandbox 변경이면 `.codex/config.toml` 동시 갱신 + 본 정책 표 갱신 안내

hook 의 알림은 same-turn / same-PR 에서 처리하는 것이 원칙. 무시한 채 commit/PR 을 올리는 것은 reviewer 가 reject 한다.

---

## 편집 규칙 (반드시 지켜야 함)

1. **skill 편집은 항상** `.claude/skills/<name>/SKILL.md` **에서만**.
   `.codex/prompts/`, `.cursor/rules/` 직접 편집 금지 — sync 가 덮어쓴다.

2. **skill 변경 PR 마다** 반드시 다음을 PR 체크리스트로 확인:
   - [ ] `.claude/skills/<name>/SKILL.md` 수정 완료
   - [ ] `python3 scripts/agents/sync-skills.py` 실행
   - [ ] `git diff .codex` 결과를 같은 PR 에 commit

3. **config 변경 PR 마다**:
   - `.claude/settings.json` 의 `permissions` / `sandbox` 변경 → `.codex/config.toml` 도 동시 갱신
   - hook 변경 → Claude 한쪽만, AGENT_CONFIG.md 의 미지원 표시 유지

4. **개인 설정**:
   - Claude: `.claude/settings.local.json` (gitignored)
   - Codex: `.codex/config.local.toml` (gitignored)
   - 양쪽에서 절대 동기화하지 않는다 — 개인 영역.

---

## 새 skill 을 추가할 때

1. `.claude/skills/<new-name>/SKILL.md` 작성 (frontmatter `name`, `description` 필수).
2. 필요시 `.claude/skills/<new-name>/agents/<sub>.md` 등 sub-agent 파일.
3. `python3 scripts/agents/sync-skills.py` 실행 → `.codex/prompts/<new-name>.md`, `.cursor/rules/<new-name>.mdc` 자동 생성.
4. PR 본문에 다음 체크리스트:
   - [ ] 프로젝트 특화인가? (Yes 만 공유; 범용 도구라면 개인 디렉토리)
   - [ ] description trigger 가 다른 skill 과 겹치지 않는가?
   - [ ] 외부 secret/token 을 포함하지 않는가?
   - [ ] 5 명 이상 팀원이 사용 의향이 있는가?
5. reviewer 1 명 승인.

개인 skill (sync 안 함) 은 `.claude/skills/improve-token-efficiency/`, `.claude/skills/ai-readiness-cartography/` 처럼 sync 스크립트의 `PERSONAL_SKILLS` set 에 추가하면 mirror 에서 제외되고 `.gitignore` 에서 자동 ignore.

---

## sync-skills.py 의 변환 로직 (참고)

| Claude `SKILL.md` frontmatter | Codex `prompts/<name>.md` | Cursor `rules/<name>.mdc` |
|---|---|---|
| `name: <x>` | `name: <x>` 그대로 | (없음) |
| `description: <x>` (single-line 또는 `>` 블록) | `description: <x>` 그대로 | `description: <x>` 그대로 |
| body markdown | "GENERATED FROM..." 배너 + body | "GENERATED FROM..." 배너 + `globs`, `alwaysApply: false` + body |
| sub-agent 디렉토리 (`agents/`) 존재 | 생성된 prompt 상단에 "Codex 가 multi-agent dispatch 미지원" NOTE | 동일 NOTE |

Cursor 의 `globs: ['**/*']`, `alwaysApply: false` 는 default 안전값. 특정 skill 만 자동 적용하고 싶다면 SSoT 의 SKILL.md 에 추가 frontmatter 키를 넣고 sync 스크립트를 확장한다 (별도 PR).

---

## CI 검증 (후속 RFC)

본 PR1 에는 포함하지 않음. 안정화 후 GitHub Actions 에 다음 추가 예정:

```yaml
- run: python3 scripts/agents/sync-skills.py
- run: git diff --exit-code .codex .cursor || (echo "::error::skill sync 결과를 commit 해주세요"; exit 1)
```
