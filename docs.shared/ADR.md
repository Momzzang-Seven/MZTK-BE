# Architecture Decision Records (ADR)

이 파일은 MZTK-BE 의 주요 아키텍처 결정 기록 인덱스다.
세부 결정은 향후 `docs.shared/decisions/ADR-XXX-<slug>.md` 로 분리해 작성한다.

## 양식

각 ADR 항목은 다음 4 가지를 담는다:

- **상태**: Proposed / Accepted / Deprecated / Superseded
- **컨텍스트**: 왜 이 결정이 필요했는지 (제약/문제/배경)
- **결정**: 무엇을 채택했는지 (대안 거부 이유 포함)
- **결과**: 도입 후 영향 (긍정/부정/추적 항목)

## 등록된 결정

| ID | 제목 | 상태 | 일자 |
|----|------|------|------|
| [ADR-001](#adr-001-agentsmd-를-단일-ai-컨텍스트-진본으로-채택) | AGENTS.md 를 단일 AI 컨텍스트 진본으로 채택 | Accepted | 2026-05-09 |
| [ADR-002](#adr-002-docssharedshared--docslocal-분리로-팀-공유-문서-게이팅) | docs.shared/ + docs.local/ 분리로 팀 공유 문서 게이팅 | Accepted | 2026-05-09 |
| [ADR-003](#adr-003-도구-중립-agentsskills-ssot--환경별-symlink-채택) | 도구 중립 `.agents/skills/` SSoT + 환경별 symlink 채택 | Accepted | 2026-05-09 |

---

### ADR-001: AGENTS.md 를 단일 AI 컨텍스트 진본으로 채택

**상태**: Accepted (2026-05-09)

**컨텍스트**

팀이 Claude Code / Codex CLI 를 병행 사용. 각 도구가 인식하는 진본 파일이 다르다 (Claude → CLAUDE.md, Codex → AGENTS.md). 또한 그동안 모든 컨텍스트 파일이 .gitignore 되어 있어 팀원마다 별개의 컨텍스트를 구축한 상태였다.

**결정**

`AGENTS.md` 를 5 scope (root, src, src/main, src/test, docs.shared) 의 진본으로 두고, 같은 디렉토리에 `CLAUDE.md` 를 1줄 wrapper (`@AGENTS.md`) 로 둔다. 양 파일은 git 추적된다. Claude Code 와 Codex 모두 동일한 본문을 읽는다. 필요 시 더 깊은 sub-scope (예: `src/test/java/.../integration/e2e/AGENTS.md`) 를 추가할 수 있다 — sub-scope 에는 CLAUDE.md wrapper 를 두지 않아도 된다 (Claude Code 가 상위 CLAUDE.md → AGENTS.md import chain 으로 이미 같은 본문을 따라가기 때문).

대안 — Symlink (CLAUDE.md → AGENTS.md): Windows symlink 호환성 문제로 거부. 대안 — 두 파일 별개 유지: scope 마다 2 파일이 되어 drift 위험으로 거부.

**결과**

- (긍정) Claude/Codex 사용자가 동일 컨텍스트로 작업
- (긍정) 새 AI 도구 도입 시 별도 마이그레이션 불필요
- (추적) wrapper 파일을 직접 편집하면 안 된다는 컨벤션을 ONBOARDING.md/PR template 에 명시

---

### ADR-002: docs.shared/ + docs.local/ 분리로 팀 공유 문서 게이팅

**상태**: Accepted (2026-05-09)

**컨텍스트**

기존 `docs/` 폴더 전체가 .gitignore 되어 있어 팀 공유 가치가 분명한 ARCHITECTURE.md, EXTERNAL_SYSTEM_SYNC.md 등이 루트에 분산되고, 팀원 개인의 design_docs/runbook/analysis 등은 git 에 들어가지 않는 상태가 같이 굳어졌다. 일괄 unlock 하면 개인 작업 메모까지 PR 에 끼어드는 noise 가 발생한다.

**결정**

새 폴더 `docs.shared/` (git 추적) 를 신설해 팀 합의된 문서만 그 안에 둔다. 기존 `docs/` 는 `docs.local/` 로 rename 후 .gitignore 유지 (개인 작업용). 루트의 ARCHITECTURE.md / EXTERNAL_SYSTEM_SYNC.md 는 `docs.shared/` 로 `git mv` 한다. 향후 `docs.local/` 의 일부 서브폴더는 팀 합의 후 elevate PR 로 `docs.shared/` 에 옮길 수 있다.

대안 — 루트 두 파일 유지 + docs.shared/ 신설 (hybrid): 기존 외부 링크 보존 측면에서 매력 있으나 "팀 공유 문서가 한 곳에 있다" 가 깨짐. 거부.

**결과**

- (긍정) 팀 공유/개인 문서가 폴더 단위로 명확히 분리
- (부정) 외부 링크 (PR 본문, Notion, Slack) 가 `ARCHITECTURE.md` 직접 참조하던 경우 깨짐 → PR1 본문에 변경 안내
- (추적) 모든 팀원이 `mv docs docs.local` 1회 실행 필요

---

### ADR-003: 도구 중립 `.agents/skills/` SSoT + 환경별 symlink 채택

**상태**: Accepted (2026-05-09)

**컨텍스트**

각 AI 도구가 자동 검색하는 skill 디렉토리가 다르다:
- Claude Code: `.claude/skills/<name>/SKILL.md` 만 (한 단계 깊이)
- Codex CLI: `.agents/skills/` (그리고 `.codex/skills/`) — BFS 재귀 (max depth 6) + symlink follow ([loader.rs](https://github.com/openai/codex/blob/main/codex-rs/core-skills/src/loader.rs))

같은 SKILL.md 본문을 두 도구에서 모두 인식시켜야 한다. 두 디렉토리에 두 벌을 두면 drift 가 즉시 발생한다.

이전(이 ADR 의 첫 버전, 단방향 sync) 안은 `.claude/skills/` 를 source 로 하고 Python 스크립트 (`sync-skills.py`) 가 `.codex/prompts/<name>.md` 를 자동 생성하는 mirror 구조였으나, 다음 비용이 누적됐다:
- sync-skills.py 자체 + drift 검사 hook (`check-claude-codex-sync.py`) 유지보수
- skill PR 마다 sync 결과 commit 의무 (체크리스트 의존)
- Codex CLI 가 실제로는 symlink 와 nested 디렉토리 검색을 모두 지원함이 확인되어 mirror 자체가 불필요했다는 점

**결정**

진본 디렉토리를 도구 중립명인 `.agents/skills/<name>/SKILL.md` 로 둔다 (git tracked).
`.claude/skills` 는 환경별로 `.agents/skills` 를 가리키는 symlink (POSIX) 또는 directory junction (Windows) 으로 만든다 — `scripts/agents/setup-skill-links.py` 가 cross-platform 으로 1회 생성한다 (멱등). git 은 이 link 자체를 추적하지 않는다 (`.gitignore: .claude/skills`). Claude Code 는 `.claude/skills/` 를, Codex CLI 는 `.agents/skills/` 를 직접 자동 검색하지만 **물리적으로 같은 한 벌의 SKILL.md** 를 본다.

PostToolUse hook 도 양쪽이 같은 스크립트를 호출한다: `.claude/settings.json` 과 `.codex/config.toml` 에 동등한 entry 를 둬서 `scripts/agents/hooks/check-architecture-rules.py` 와 `check-agent-link.py` (link 무결성 검사) 를 발화시킨다.

개인 skill 은 `.agents/skills/private-<name>/` prefix 컨벤션. 이유: Claude Code 가 한 단계 깊이만 검색하므로 `.agents/skills/private/<name>/` 같은 nested 그룹화는 silent 무시된다. prefix 로 통일해 양쪽 도구에서 모두 인식되며, `.gitignore` 의 `.agents/skills/private-*/` glob 한 줄로 일괄 untracked.

대안 — `.claude/skills/` 를 source 로 두고 sync (이전 안): mirror 인프라 유지 비용 ↑, 두 벌 데이터 drift 위험. 거부.
대안 — `.agents/skills/` 를 git 에 symlink 로 직접 commit: Windows native 사용자 호환성 문제 (POSIX symlink commit 은 Windows checkout 에서 일반 파일로 풀림). 거부.
대안 — `.agents/skills/private/<sub>/` 디렉토리 그룹화: Claude Code 의 한 단계 검색 한계로 silent 무시. 거부.

**결과**

- (긍정) 두 도구가 같은 SKILL.md 를 본다 — sync 작업, drift 검증, 충돌 가드 모두 폐기
- (긍정) 새 AI 도구 도입 시 `.agents/skills/` 를 가리키는 환경별 link 만 추가하면 됨
- (부정) 신규 합류자는 첫 clone 후 `python3 scripts/agents/setup-skill-links.py` 1회 실행이 필요. 빠뜨리면 Claude 측에서 skill 미인식 — `check-agent-link.py` 가 PostToolUse 에서 안내
- (추적) Claude Code `/skills` 슬래시 명령이 symlink 안의 skill 목록을 일부 표시 못 함 ([Issue #14836](https://github.com/anthropics/claude-code/issues/14836)). 실제 트리거링/실행은 정상이라 기능 영향 없음. 상위 fix 시 자동 해소.

@AGENTS.local.md
