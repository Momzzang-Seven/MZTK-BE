# AGENT_CONFIG.md — AI 도구 간 config / skill 동기화 정책

이 프로젝트는 **Claude Code, Codex CLI** 사용자가 공존한다.
설정과 skill 의 분산을 막기 위해 다음 규칙을 따른다.

---

## 단일 진실 원천 (SSoT)

| 항목 | SSoT 위치 | 다른 도구에서의 인식 방법 |
|------|----------|---------------------------|
| Project instructions | `AGENTS.md` (모든 scope) | 자동 — Claude Code, Codex CLI 모두 직접 읽음 |
| Skills / Custom commands | `.agents/skills/<name>/SKILL.md` | Codex CLI 는 `.agents/skills/` 자동 검색. Claude Code 는 `.claude/skills/` 만 검색하므로 환경별로 `.claude/skills → .agents/skills` link 를 둠 (POSIX symlink / Windows directory junction) |
| Project permissions | `.claude/settings.json` (Claude) · `.codex/config.toml` (Codex) | **양쪽이 진본** — permissions/sandbox 변경 시 두 파일 동시 갱신 |
| PostToolUse hooks | `.claude/settings.json` `hooks.PostToolUse` (Claude) · `.codex/config.toml` `[[hooks.PostToolUse]]` (Codex) | 양쪽이 같은 스크립트 (`scripts/agents/hooks/*.py`) 를 호출. 추가/삭제 시 두 파일 동시 갱신 |

`.claude/skills/` 는 git 에서 추적하지 않는다. 신규 합류자는 첫 clone 후 1 회 `python3 scripts/agents/setup-skill-links.py` 를 실행해 link 를 만든다 — 그러면 두 도구가 **물리적으로 같은 SKILL.md 한 벌**을 본다.

---

## 자동 강제 (PostToolUse hook)

`docs.shared/AGENT_CONFIG.md` 는 정책 문서일 뿐이라 사람이나 AI 가 무시할 수 있다. 이를 보완하기 위해 `scripts/agents/hooks/check-agent-link.py` 가 PostToolUse 시점에 다음을 자동 검사한다 (informational, 차단 아님):

- `.claude/skills` link 가 존재하는가
- 그 link 가 `.agents/skills` 디렉토리를 가리키며 deref 가능한가

위반 시 `additionalContext` 로 `python3 scripts/agents/setup-skill-links.py` 실행 안내. 같은 hook 이 Claude Code (`.claude/settings.json`) 와 Codex CLI (`.codex/config.toml`) 양쪽에서 동일하게 발화한다.

ARCHITECTURE.md 위반은 `scripts/agents/hooks/check-architecture-rules.py` 가 검출 — 자세한 내용은 `src/main/AGENTS.md` 참조.

---

## 편집 규칙 (반드시 지켜야 함)

1. **skill 편집은 항상** `.agents/skills/<name>/SKILL.md` **에서만**.
   `.claude/skills/<name>/...` 경로로 편집해도 link 라 결과는 동일하지만, 새 파일 추가/삭제 시 link 를 통한 조작이 plumbing 도구에 따라 어색할 수 있어 직접 `.agents/skills/` 를 수정하는 것을 권장.

2. **skill 변경 PR**: `.agents/skills/<name>/...` 변경분만 commit 하면 끝. mirror sync · `.codex/prompts/` 갱신 같은 후속 작업 없음.

3. **config 변경 PR**:
   - `.claude/settings.json` 의 `permissions` / `sandbox` 변경 → `.codex/config.toml` 도 동시 갱신
   - PostToolUse hook 추가/삭제/scriptPath 변경 → 두 파일에 동등한 entry 가 있어야 함
   - 정책 자체 변경 (skill/permission/hook 운영 규칙) → 본 문서 표 갱신

4. **개인 설정**:
   - Claude: `.claude/settings.local.json` (gitignored)
   - Codex: `.codex/config.local.toml` (gitignored)
   - 양쪽에서 절대 동기화하지 않는다 — 개인 영역.

---

## 새 skill 을 추가할 때

1. `.agents/skills/<new-name>/SKILL.md` 작성 (frontmatter `name`, `description` 필수). frontmatter `name` 은 디렉토리 이름과 일치시킨다.
2. 필요시 `.agents/skills/<new-name>/agents/<sub>.md` 등 sub-agent 파일.
3. PR 본문에 다음 체크리스트:
   - [ ] 프로젝트 특화인가? (Yes 만 공유; 범용 도구라면 개인 디렉토리)
   - [ ] description trigger 가 다른 skill 과 겹치지 않는가?
   - [ ] 외부 secret/token 을 포함하지 않는가?
   - [ ] 5 명 이상 팀원이 사용 의향이 있는가?
4. reviewer 1 명 승인.

개인 skill (각자 환경에만 두는 범용 도구) 은 `.agents/skills/private-<name>/` 형태로 만든다. `private-` prefix 가 붙은 디렉토리는 `.gitignore` 의 `.agents/skills/private-*/` glob 한 줄로 자동 ignore 되어 commit 되지 않으면서, 두 도구 모두에서 정상 인식된다 (SKILL.md frontmatter `name` 도 `private-<name>` 으로 디렉토리명과 일치시킬 것).

> Why `private-` prefix? Claude Code 의 skill discovery 는 한 단계만 검색하므로 `.agents/skills/private/<name>/SKILL.md` 같은 nested 배치는 silent 무시된다. 반면 Codex CLI 는 BFS 로 재귀 검색해 인식한다 — 이 비대칭을 피하려고 디렉토리 grouping 대신 prefix 컨벤션으로 통일.

---

## Codex-only prompt 가 정말 필요할 때

희소한 케이스지만, Codex CLI 에서만 의미 있는 prompt 를 운용해야 한다면 `.codex/skills/<name>/SKILL.md` 에 작성한다 (Codex 가 자동 검색). Claude 와 공유 가치가 있으면 `.agents/skills/` 로 옮기는 게 원칙.

---

## 알려진 이슈

| 항목 | 영향 | 비고 |
|------|------|------|
| Claude Code `/skills` 슬래시 명령이 symlink 안의 skill 목록 표시를 일부 못함 ([Issue #14836](https://github.com/anthropics/claude-code/issues/14836)) | UI 표시 이슈만, 실제 트리거링/실행은 정상 | 상위 fix 시 자동 해소 |
| Codex `apply_patch` 에서 hook 신뢰성이 들쭉날쭉하다는 외부 보고 | 일부 편집이 architecture-rules 검사 우회 가능 | git pre-commit `spotlessApply` 가 백업. 향후 PreCommit 단 java rule 검사 추가 검토 (별 PR) |
