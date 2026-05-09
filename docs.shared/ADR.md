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
| [ADR-003](#adr-003-claude-native-skill-source--단방향-sync-채택) | Claude-native skill source + 단방향 sync 채택 | Accepted | 2026-05-09 |

---

### ADR-001: AGENTS.md 를 단일 AI 컨텍스트 진본으로 채택

**상태**: Accepted (2026-05-09)

**컨텍스트**

팀 7명이 각자 Claude Code, Codex CLI, Cursor 중 하나를 사용. 각 도구가 인식하는 진본 파일이 다르다 (Claude → CLAUDE.md, Codex/Cursor/Aider → AGENTS.md). 또한 그동안 모든 컨텍스트 파일이 .gitignore 되어 있어 팀원마다 별개의 컨텍스트를 구축한 상태였다.

**결정**

`AGENTS.md` 를 모든 scope (root, src, src/main, src/test, docs.shared) 의 진본으로 두고, 같은 디렉토리에 `CLAUDE.md` 를 1줄 wrapper (`@AGENTS.md`) 로 둔다. 양 파일은 git 추적된다. Claude Code 와 Codex 모두 동일한 본문을 읽는다.

대안 — Symlink (CLAUDE.md → AGENTS.md): Windows symlink 호환성 문제로 거부. 대안 — 두 파일 별개 유지: 5 scope × 2 파일 = 10 파일 drift 위험으로 거부.

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

### ADR-003: Claude-native skill source + 단방향 sync 채택

**상태**: Accepted (2026-05-09)

**컨텍스트**

각 AI 도구의 skill/custom-command 포맷이 다르다:
- Claude: `.claude/skills/<name>/SKILL.md` (YAML frontmatter + body)
- Codex: `.codex/prompts/<name>.md` (slash command)
- Cursor: `.cursor/rules/<name>.mdc` (frontmatter + body)

raewookang 이 Claude 에서 만든 9개 프로젝트 특화 skill 을 Codex/Cursor 사용자도 동일하게 쓰고 싶다. 직접 양쪽을 손으로 동기화하면 drift 가 즉시 발생한다.

**결정**

`.claude/skills/<name>/SKILL.md` 를 단일 source 로 두고, `scripts/agents/sync-skills.py` (Python 표준 라이브러리만 사용) 가 `.codex/prompts/`, `.cursor/rules/` 를 단방향 자동 생성한다. Codex/Cursor 사용자는 직접 편집 금지. skill 변경 PR 작성자가 sync 스크립트를 한 번 돌리고 결과를 같은 PR 에 commit 한다 (수동, PR 체크리스트로 강제). CI gate 는 후속 RFC.

대안 — Two-way sync: 도구 표현력 차이로 의미 손실 위험. 거부.
대안 — Pre-commit hook 자동화: 환경 의존성 (python3 미설치 등) issue 가 잦음. 거부.

**결과**

- (긍정) 한 곳에서 skill 편집 → 모든 도구 사용자가 git pull 만으로 동기 상태
- (부정) Codex 사용자가 새 skill 을 제안하려면 Claude 사용자가 작성해야 함 (역방향 도구 부재 인정)
- (추적) sync 미실행 PR 은 reviewer 가 reject. 안정화 후 CI gate 추가 검토.

@AGENTS.local.md
