# ONBOARDING.md — 신규 합류자 가이드 (AI 에이전트 환경)

처음 이 레포를 클론한 사람이 5 분 안에 정상 작동 상태로 진입하기 위한 문서.
프로덕션 코드 컨벤션은 `docs.shared/ARCHITECTURE.md` 를, 커밋 컨벤션은 `docs.shared/AGENTS.md` 를 보라.

---

## 1. 첫 setup (1 회)

```bash
git pull
mv docs docs.local                              # 기존 개인 docs 가 있다면 personal 영역으로 이동 (없으면 skip)
python3 scripts/agents/setup-skill-links.py     # .claude/skills → .agents/skills 링크 1회 생성
ls .claude/skills/                              # 9 개 공유 skill 인식 확인 (개인 skill 추가 시 더 많을 수 있음)
ls scripts/agents/hooks/                        # check-architecture-rules.py, check-agent-link.py 2 개 확인
```

`setup-skill-links.py` 는 cross-platform — macOS/Linux 는 symlink, Windows native 는 directory junction (Developer Mode 불필요, `cmd.exe` 만 있으면 동작) 을 생성한다. 멱등하므로 여러 번 실행해도 안전.

이후 도구 별:
- **Claude Code**: 그냥 `claude` 실행. AGENTS.md 자동 인식, `.claude/skills/` 링크를 통해 공용 skill 인식.
- **Codex CLI**: `codex` 실행. AGENTS.md 자동 인식, `.agents/skills/` 직접 자동 검색 (symlink follow 가능).

---

## 2. 단일 진실 원천 — 진본 위치

| 항목 | 진본 | 절대 직접 수정 금지 |
|------|------|---------------------|
| 프로젝트 컨텍스트 | `AGENTS.md` (5 scope: 루트, src, src/main, src/test, docs.shared) | 같은 디렉토리의 `CLAUDE.md` (1 줄 wrapper) |
| Skills | `.agents/skills/<name>/SKILL.md` | `.claude/skills/` (symlink 또는 junction — 환경별 자동 생성물) |
| 팀 공용 settings | `.claude/settings.json` (Claude), `.codex/config.toml` (Codex) | 양쪽 모두 진본 — permissions/sandbox 변경 시 동시 갱신 |
| 팀 공용 hook | `scripts/agents/hooks/check-architecture-rules.py`, `scripts/agents/hooks/check-agent-link.py` | (없음, 직접 편집 가능) |
| 팀 공용 docs | `docs.shared/` | (없음) |

자세한 동기화 정책은 `docs.shared/AGENT_CONFIG.md`.

---

## 3. 개인 영역 (gitignored)

각자 자기 환경에만 두는 것:

| 항목 | 위치 |
|------|------|
| 개인 AI 컨텍스트 메모 | 동일 디렉토리의 `AGENTS.local.md` (각 scope 자유) |
| Claude 개인 settings (모델 선호, 권한, 개인 hook) | `.claude/settings.local.json` |
| Codex 개인 settings | `.codex/config.local.toml` |
| 개인 skill (범용 분석 도구 등) | `.agents/skills/private-<name>/` — `private-` prefix 가 붙은 디렉토리는 `.agents/skills/private-*/` glob 으로 일괄 gitignore. SKILL.md frontmatter `name` 도 디렉토리명과 동일하게 `private-<name>` 으로 작성 |
| 작업 메모, 개인 design doc, 티켓 폴더 | `docs.local/` |

`AGENTS.local.md` 는 모든 scope 의 `AGENTS.md` 끝에 `@AGENTS.local.md` 로 import 되도록 이미 설정. 파일이 없으면 silent skip.

---

## 4. 거버넌스

| 행위 | 규칙 |
|------|------|
| 어떤 scope 의 AGENTS.md 일반 수정 | 1 reviewer 승인 |
| 루트 `/AGENTS.md` 수정 | **2 reviewer 승인** (전 작업에 영향) |
| 신규 skill 추가 | PR template 체크리스트 + 1 reviewer (`.agents/skills/<name>/SKILL.md` 1 곳만 작성) |
| 기존 skill 수정 | `.agents/skills/<name>/SKILL.md` 직접 편집 + 1 reviewer (mirror sync 작업 없음) |
| `scripts/agents/hooks/` 신규 추가 또는 v1 hook 강화 | tech lead 승인 + RFC (false positive 영향 평가 필수) |
| `docs.shared/` 신규 파일 | ADR 추가 후 합의 |
| ARCHITECTURE.md 규칙 변경 | 본문 변경 + `check-architecture-rules.py` 동시 갱신 (한 PR 안에서) |

신규 skill 체크리스트 (PR template 에 포함):

- [ ] 프로젝트 특화인가? (Yes 만 공유; 범용은 개인)
- [ ] description trigger 가 다른 skill 과 겹치지 않는가?
- [ ] 외부 secret/token 을 포함하지 않는가?
- [ ] 5 명 이상 팀원이 사용 의향이 있는가?

config 변경 체크리스트:

- [ ] `.claude/settings.json` permissions/sandbox 변경이면 `.codex/config.toml` 도 동시 갱신?
- [ ] PostToolUse hook 추가/변경 시 양쪽 settings 모두 반영?
- [ ] AGENT_CONFIG.md 의 cross-reference 표 갱신?

---

## 5. 자주 만나는 상황

**Q: AI 가 자꾸 ARCHITECTURE.md 위반을 추가한다.**
A: 정상. PostToolUse hook 이 informational 메시지를 띄우면 AI 가 다음 턴에 자가 수정한다. 차단(block) 이 아니다 — 강도 상향은 후속 RFC.

**Q: skill 을 고쳤더니 hook 이 ".claude/skills 가 비어있다" 류 메시지를 띄운다.**
A: `.claude/skills` 가 `.agents/skills` 를 가리키는 link 가 없거나 깨져 있을 때 `check-agent-link.py` 가 띄우는 메시지. `python3 scripts/agents/setup-skill-links.py` 를 한 번 실행하면 해소된다. 새 clone 직후 setup 을 빼먹은 경우가 가장 흔하다.

**Q: settings.json 을 손댔더니 Codex 사용자 환경과 어긋나는 게 걱정된다.**
A: `.claude/settings.json` 의 permissions/sandbox 를 바꿨다면 `.codex/config.toml` 의 `[sandbox]` 도 같은 PR 에서 갱신. PostToolUse hook entry 도 양쪽이 동등하도록 동시 수정. 정책 표 변경이라면 `docs.shared/AGENT_CONFIG.md` 도 함께.

**Q: Claude 에 새 skill 을 추가했는데 Codex 사용자가 못 본다.**
A: `.agents/skills/<name>/SKILL.md` 한 곳만 작성하면 Claude Code 는 `.claude/skills` symlink 를 통해, Codex CLI 는 `.agents/skills/` 를 직접 자동 검색해 같은 파일을 인식한다. 만약 Codex 측에서 안 보이면 (1) 신규 skill 디렉토리/파일이 commit 됐는지, (2) Codex 가 repo trust 등록됐는지 확인.

**Q: PR 머지 후 내 환경에서 docs/ 가 untracked 로 표시된다.**
A: `mv docs docs.local` 1 회 실행. `.gitignore` 가 두 이름 모두 무시한다.

**Q: hook 이 에러를 내거나 작업을 막는다.**
A: hook 코드 전체가 try/except 로 감싸져 있어 정상적으로는 silent fail 한다. 작업 막힘이 발생하면 `.claude/settings.json` 또는 `.codex/config.toml` 의 해당 hook entry 를 일시적으로 주석 처리한 뒤 issue 등록.

**Q: Claude `/skills` 슬래시 명령이 일부 skill 을 표시하지 않는다.**
A: 알려진 이슈 ([anthropics/claude-code#14836](https://github.com/anthropics/claude-code/issues/14836)) 로 symlink 디렉토리 안의 skill 목록 표시가 부분적으로 누락될 수 있다. 실제 트리거링/실행은 정상이라 기능 영향 없음. 상위 fix 시 자동 해소.

**Q: Codex 단독으로만 쓸 prompt 를 추가하고 싶다.**
A: `.codex/skills/<name>/SKILL.md` 를 만들면 Codex 만 인식한다. Claude 와 공유할 의향이면 `.agents/skills/<name>/SKILL.md` 로 작성.

---

## 6. 관련 문서

- `docs.shared/ARCHITECTURE.md` — Hexagonal Architecture rule (모든 모듈)
- `docs.shared/EXTERNAL_SYSTEM_SYNC.md` — DB ↔ 외부시스템 트랜잭션 동기화
- `docs.shared/ADR.md` — 주요 아키텍처 결정 기록 (이번 통합 정책 포함: ADR-001/002/003)
- `docs.shared/AGENT_CONFIG.md` — 도구 간 설정 동기화 표
- `docs.shared/AGENTS.md` — Conventional Commits + docs.local 가이드
