# docs.shared/ — Team-shared Documentation

이 폴더의 모든 파일은 팀 합의로 유지·관리되는 진본이다.
개인/티켓 단위 작업 메모는 `docs.local/` (gitignored) 에 둔다.

## 현재 파일

- `ARCHITECTURE.md` — Hexagonal Architecture 가이드 (모든 모듈의 레이어/패키지 컨벤션)
- `EXTERNAL_SYSTEM_SYNC.md` — DB ↔ 외부시스템 (KMS, S3, RPC, 3rd-party API) 트랜잭션 동기화 규칙
- `ADR.md` — Architecture Decision Records 인덱스
- `AGENT_CONFIG.md` — AI 도구 (Claude Code, Codex, Cursor) 간 config 동기화 표
- `AGENTS.md` — 본 파일
---
## Commit Conventions

Commit Message는 반드시 아래 형식을 따라야 합니다.

```text
[Jira Ticket] [Prefix]: [Commit Summary]
[Commit Detail](option)

[Co-author](option)

# Example
[MOM-4] feature: Title Screen과 Game Setting Screen 연결
Play 버튼 작동시 Game Screen이 아닌 Game Setting Screen 으로 이동
```

### Commit Message Prefix
Commit 메시지는 Conventional Commits 를 따른다. 한국어 허용. 코드 주석은 영어 (Javadoc 형식).

| Type | When to use |
|------|-------------|
| `feature` | New feature |
| `fix` | Bug fix |
| `!hotfix` | Critical production hotfix |
| `refactor` | Production code refactoring |
| `comment` | Add or update comments |
| `docs` | Documentation only |
| `test` | Test code only, no production code change |
| `chore` | Build config, package manager, tooling updates |
| `rename` | File or directory rename/move only |
| `remove` | File deletion only |
| `!rebase` | Commits generated during a rebase |

### Commit Summary

Commit Message의 요약은 한글 혹은 영어로 작성합니다. 너무 길어지지 않도록 간결하게 작성합니다. 내용이 많다면 다음줄에 Commit Detail 부분에 작성합니다.

---
## Branch Conventions

Branch를 생성할 때 다음 규칙을 지켜 Branch 이름을 정합니다.

```bash
[Prefix]/[Jira Ticket]-[BranchName]

# 브랜치 이름 예시
feature/MOM-3-Kakaotalk_login_implementation
```

### Branch Name Prefix

Branch Name의 Prefix는 해당 Branch와 매칭되는 Jira 이슈 유형에 따라 정해집니다. 소문자로 작성합니다.

| Branch Name Prefix | 이슈 유형 |
| --- | --- |
| feature | 기능 개발 |
| refactor | 개선 |
| bugfix | 버그 수정 |
| hotfix | 버그 수정 |
| chore | 기타 |

### Branch Name

해당 Branch의 작업을 요약하는 이름을 짓습니다. 반드시 영어로 지어야 하며 단어는 대문자로 시작합니다. 4개 단어를 넘어가지 않게 작성합니다.

---
## docs.local/ 디렉토리 가이드 (informational, 개인 영역)

각자 `docs.local/` 하위에서 다음 컨벤션을 권장한다 (강제 아님). 팀 공유 가치가 분명한 산출물은 `docs.shared/` 하위로 elevate PR 를 분리해 올린다.

- `design_docs/` — feature blueprints (before implementation)
- `design/` — supplementary design notes referenced from design_docs
- `implementation_docs/` — commit-by-commit implementation plans
- `api_docs/` — frontend-facing API specs
- `test/` — test case documentation
- `decisions/` — Architecture Decision Records (ADRs); one file per key design decision
- `analysis/` — ad-hoc investigation memos (perf, incident, postmortem)
- `refactor/` — refactor plans and migration notes
- `review/` — code/design review notes and follow-up trackers
- `security/` — threat models, security review write-ups
- `ci/` — CI workflow notes (rules, recipes)
- `cicd/` — full CI/CD pipeline docs (deploy targets, rollout)
- `runbook/` — operator runbooks (rotation, incident-response procedures)
- `S3/` — S3 storage / media-asset operational notes
- `MOM-XXX/` — ticket-scoped working folder (see corresponding JIRA)
- `pr-description/` — auto-generated PR descriptions; filename = branch name

@AGENTS.local.md
