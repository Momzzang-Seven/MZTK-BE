# ONBOARDING.md — 신규 합류자 가이드 (AI 에이전트 환경)

처음 이 레포를 클론한 사람이 5 분 안에 정상 작동 상태로 진입하기 위한 문서.
프로덕션 코드 컨벤션은 `docs.shared/ARCHITECTURE.md` 를, 커밋 컨벤션은 `docs.shared/AGENTS.md` 를 보라.

---

## 1. 첫 setup (1 회)

```bash
git pull
mv docs docs.local      # 기존 개인 docs 가 있다면 personal 영역으로 이동 (없으면 skip)
ls .claude/skills/      # 9 개 공유 skill 인식 확인
ls .claude/hooks/       # check-architecture-rules.py, check-claude-codex-sync.py 2 개 확인
```

이후 도구 별:
- **Claude Code**: 그냥 `claude` 실행. AGENTS.md 자동 인식.
- **Codex CLI**: `codex` 실행. AGENTS.md 자동 인식. `.codex/prompts/` 의 9 개 prompt 사용 가능.
- **Cursor 등 기타 도구**: AGENTS.md 는 그대로 활용 가능. 단 skill / config 자동 sync 는 본 PR1 범위 밖 (개인이 알아서 매핑).

---

## 2. 단일 진실 원천 — 진본 위치

| 항목 | 진본 | 절대 직접 수정 금지 |
|------|------|---------------------|
| 프로젝트 컨텍스트 | `AGENTS.md` (5 scope: 루트, src, src/main, src/test, docs.shared) | 같은 디렉토리의 `CLAUDE.md` (1 줄 wrapper) |
| Skills | `.claude/skills/<name>/SKILL.md` | `.codex/prompts/` (auto-generated) |
| 팀 공용 settings | `.claude/settings.json` | (없음, 직접 편집 가능) |
| 팀 공용 hook (architecture) | `.claude/hooks/check-architecture-rules.py` | (없음, 직접 편집 가능) |
| 팀 공용 hook (sync) | `.claude/hooks/check-claude-codex-sync.py` | (없음, 직접 편집 가능) |
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
| 개인 skill (범용 분석 도구 등) | `.claude/skills/<name>/` 후 `sync-skills.py` `PERSONAL_SKILLS` set 에 등록 |
| 작업 메모, 개인 design doc, 티켓 폴더 | `docs.local/` |

`AGENTS.local.md` 는 모든 scope 의 `AGENTS.md` 끝에 `@AGENTS.local.md` 로 import 되도록 이미 설정. 파일이 없으면 silent skip.

---

## 4. 거버넌스

| 행위 | 규칙 |
|------|------|
| 어떤 scope 의 AGENTS.md 일반 수정 | 1 reviewer 승인 |
| 루트 `/AGENTS.md` 수정 | **2 reviewer 승인** (전 작업에 영향) |
| 신규 skill 추가 | PR template 체크리스트 + 1 reviewer + sync 결과 commit |
| 기존 skill 수정 | sync 결과 commit (체크리스트 강제) + 1 reviewer |
| `.claude/hooks/` 신규 추가 또는 v1 hook 강화 | tech lead 승인 + RFC (false positive 영향 평가 필수) |
| `docs.shared/` 신규 파일 | ADR 추가 후 합의 |
| ARCHITECTURE.md 규칙 변경 | 본문 변경 + `check-architecture-rules.py` 동시 갱신 (한 PR 안에서) |

신규 skill 체크리스트 (PR template 에 포함):

- [ ] 프로젝트 특화인가? (Yes 만 공유; 범용은 개인)
- [ ] description trigger 가 다른 skill 과 겹치지 않는가?
- [ ] 외부 secret/token 을 포함하지 않는가?
- [ ] 5 명 이상 팀원이 사용 의향이 있는가?
- [ ] `python3 scripts/agents/sync-skills.py` 실행 후 결과 (`.codex/prompts/`) 같은 PR 에 commit?

config 변경 체크리스트:

- [ ] `.claude/settings.json` 변경이면 `.codex/config.toml` 의 동등 설정도 갱신?
- [ ] AGENT_CONFIG.md 의 cross-reference 표 갱신?

---

## 5. 자주 만나는 상황

**Q: AI 가 자꾸 ARCHITECTURE.md 위반을 추가한다.**
A: 정상. PostToolUse hook 이 informational 메시지를 띄우면 AI 가 다음 턴에 자가 수정한다. 차단(block) 이 아니다 — 강도 상향은 후속 RFC.

**Q: skill 을 고쳤더니 hook 이 "Codex mirror 가 stale 합니다" 라는 메시지를 띄운다.**
A: 정상. `.claude/skills/<name>/SKILL.md` 또는 `.claude/skills/<name>/agents/*.md` 가 수정될 때마다 `check-claude-codex-sync.py` 가 `sync-skills.py --check` 를 돌려 mirror 가 최신인지 검증한다. 메시지가 뜨면 같은 turn 안에서 `python3 scripts/agents/sync-skills.py` 실행하고 결과를 commit 하면 된다.

**Q: settings.json 을 손댔더니 hook 이 .codex/config.toml 갱신 알림을 띄운다.**
A: 정상. `check-claude-codex-sync.py` 는 settings.json 변경 시 무조건 알림을 띄운다 (실제 변경 내용 분석은 안 함). 본인이 손댄 부분이 permissions/sandbox 였다면 `.codex/config.toml` 도 동시 갱신 + AGENT_CONFIG.md 표 갱신, hook 이나 statusMessage 만 수정한 거라면 알림 무시.

**Q: Claude 에 새 skill 을 추가했는데 Codex 사용자가 못 본다.**
A: `python3 scripts/agents/sync-skills.py` 안 돌렸을 가능성. 위 sync hook 이 알림을 띄웠는지 확인. 같은 PR 에 sync 결과 (`.codex/prompts/`) 가 포함됐는지 확인.

**Q: PR 머지 후 내 환경에서 docs/ 가 untracked 로 표시된다.**
A: `mv docs docs.local` 1 회 실행. `.gitignore` 가 두 이름 모두 무시한다.

**Q: Codex 사용자인데 새 skill 을 제안하고 싶다.**
A: GitHub issue 또는 design 단계에서 제안. Claude 사용자가 `.claude/skills/<new-name>/SKILL.md` 를 작성하고 sync 스크립트 실행. 역방향 작성 도구는 현재 미제공 (ADR-003 참고).

**Q: hook 이 에러를 내거나 작업을 막는다.**
A: hook 코드 전체가 try/except 로 감싸져 있어 정상적으로는 silent fail 한다. 작업 막힘이 발생하면 `.claude/hooks/check-architecture-rules.py` 를 일시적으로 `mv check-architecture-rules.py check-architecture-rules.py.bak` 한 뒤 issue 등록.

---

## 6. 관련 문서

- `docs.shared/ARCHITECTURE.md` — Hexagonal Architecture rule (모든 모듈)
- `docs.shared/EXTERNAL_SYSTEM_SYNC.md` — DB ↔ 외부시스템 트랜잭션 동기화
- `docs.shared/ADR.md` — 주요 아키텍처 결정 기록 (이번 통합 정책 포함: ADR-001/002/003)
- `docs.shared/AGENT_CONFIG.md` — 도구 간 설정 동기화 표
- `docs.shared/AGENTS.md` — Conventional Commits + docs.local 가이드
