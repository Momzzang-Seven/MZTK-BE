# MZTK-BE 개발자 가이드

이 문서는 MZTK-BE를 개발할 때 사람과 AI Agent가 함께 참고하는 시작점입니다.

`DEV.md`는 모든 규칙을 길게 복사해 두는 문서가 아닙니다. 여기서는 개발 흐름, 확인 순서, PR 전 점검, CI/보안 체크만 안내합니다. 사람은 `DEV.md`, `ONBOARDING.md`, `PROD.md`, `docs.shared/` 문서를 중심으로 보고, AI Agent는 추가로 범위별 `AGENTS.md`를 따라갑니다.

---

## 1. 문서의 역할

이 문서에서 다루는 내용은 다음과 같습니다.

- 신규 개발자가 프로젝트를 파악하는 순서
- 로컬 개발을 시작하는 기본 흐름
- 기능 개발 전에 확인해야 할 문서
- PR 전에 실행해야 할 검증
- CI와 보안 체크에서 주의해야 할 항목
- AI Agent가 작업할 때 추가로 따라야 할 문서

이 문서에 남기지 않는 내용도 명확히 구분합니다.

- 실제 환경 변수 값
- 운영 secret과 운영 배포 절차
- 지갑 private key, RPC secret, AWS credential
- DB seed 값이나 보안상 민감한 초기화 절차
- 운영 장애 대응 runbook

운영 배포나 운영 환경 변경은 `PROD.md`를 따릅니다.

---

## 2. 문서 계층과 참조 순서

이 프로젝트는 디렉터리 계층마다 문서가 나뉘어 있습니다. 작업 위치가 깊어질수록 더 가까운 문서를 함께 확인합니다.

사람 개발자는 `DEV.md`, `ONBOARDING.md`, `PROD.md`, `docs.shared/` 문서를 중심으로 봅니다. AI Agent는 여기에 더해 각 계층의 `AGENTS.md`를 부모 계층에서 자식 계층 순서로 읽습니다.

**Repository root**

- 사람: [ONBOARDING.md](ONBOARDING.md), [DEV.md](DEV.md), [PROD.md](PROD.md)
- AI Agent: [AGENTS.md](AGENTS.md)
- 목적: 신규 합류, 로컬 개발, 운영 문서 구분

**Shared docs**

- 사람: [docs.shared/ARCHITECTURE.md](docs.shared/ARCHITECTURE.md), [docs.shared/EXTERNAL_SYSTEM_SYNC.md](docs.shared/EXTERNAL_SYSTEM_SYNC.md), [docs.shared/ADR.md](docs.shared/ADR.md), [docs.shared/AGENT_CONFIG.md](docs.shared/AGENT_CONFIG.md)
- AI Agent: [docs.shared/AGENTS.md](docs.shared/AGENTS.md)
- 목적: 아키텍처, 외부 시스템 동기화, Agent 설정, Git 규칙 확인

**Source root**

- 사람: 기존 source 구조와 모듈 코드
- AI Agent: [src/AGENTS.md](src/AGENTS.md)
- 목적: 모듈 위치와 source 전체 규칙 확인

**Production code**

- 사람: 기존 `src/main` 코드와 관련 모듈 패턴
- AI Agent: [src/main/AGENTS.md](src/main/AGENTS.md)
- 목적: 프로덕션 코드 작성, DB profile, security 규칙 확인

**Test code**

- 사람: [src/test/java/momzzangseven/mztkbe/README.md](src/test/java/momzzangseven/mztkbe/README.md), 기존 테스트 코드
- AI Agent: [src/test/AGENTS.md](src/test/AGENTS.md)
- 목적: 테스트 계층, E2E 조건, 테스트 작성 규칙 확인

**E2E test**

- 사람: 기존 E2E 테스트 코드
- AI Agent: [src/test/java/momzzangseven/mztkbe/integration/e2e/AGENTS.md](src/test/java/momzzangseven/mztkbe/integration/e2e/AGENTS.md)
- 목적: `E2ETestBase`, `DatabaseCleaner`, cleanup 규칙 확인

**DB migration**

- 사람: [src/main/resources/db/migration/README.md](src/main/resources/db/migration/README.md)
- AI Agent: [AGENTS.md](AGENTS.md), [src/AGENTS.md](src/AGENTS.md), [src/main/AGENTS.md](src/main/AGENTS.md)
- 목적: migration 작성과 검증 흐름 확인

**Module-specific**

- 사람: 기존 모듈 코드와 가까운 README/report 문서
- AI Agent: 예를 들어 [src/main/java/momzzangseven/mztkbe/modules/web3/qna/AGENTS.md](src/main/java/momzzangseven/mztkbe/modules/web3/qna/AGENTS.md)
- 목적: 특정 모듈에만 적용되는 예외나 제약 확인

참조 순서는 아래처럼 잡습니다.

1. 먼저 repository root의 사람용 문서를 확인합니다.
2. 작업이 아키텍처나 외부 시스템과 관련되면 `docs.shared/`의 기준 문서를 확인합니다.
3. 작업 디렉터리에 가까운 README나 report 문서가 있으면 함께 확인합니다.
4. AI Agent가 작업한다면 root `AGENTS.md`부터 작업 디렉터리에 가까운 `AGENTS.md`까지 순서대로 확인합니다.
5. 같은 주제가 여러 문서에 있다면 개발 흐름은 `DEV.md`, 신규 합류 흐름은 `ONBOARDING.md`, 팀 정책은 `docs.shared/`, AI 실행 방식은 가장 가까운 `AGENTS.md`를 기준으로 봅니다.

현재 branch, commit 규칙은 [docs.shared/AGENTS.md](docs.shared/AGENTS.md)에 함께 정리되어 있습니다. 문서명은 `AGENTS.md`지만 팀 공통 Git 규칙도 포함되어 있으므로, 사람 개발자는 Git 규칙을 확인할 때만 해당 문서를 참조하면 됩니다.

[README.md](README.md)는 프로젝트 소개 문서입니다. 로컬 실행, 개발 절차, PR 전 검증은 README가 아니라 이 문서와 `ONBOARDING.md`를 기준으로 합니다.

`docs.shared/`는 팀이 함께 관리하는 문서입니다. 개인 메모, 티켓별 초안, 실험 결과는 `docs.local/`에 둡니다.

---

## 3. AI Agent 참조 방식

`AGENTS.md` 계열 문서는 사람용 매뉴얼이 아니라 AI Agent에게 작업 규칙을 전달하기 위한 문서입니다.

AI Agent가 작업할 때는 2장의 계층 목록을 기준으로, 현재 작업 파일에 영향을 주는 `AGENTS.md`를 부모에서 자식 순서로 읽습니다.

예를 들어 production code를 수정한다면 [AGENTS.md](AGENTS.md), [src/AGENTS.md](src/AGENTS.md), [src/main/AGENTS.md](src/main/AGENTS.md)를 함께 확인합니다. E2E 테스트를 수정한다면 [AGENTS.md](AGENTS.md), [src/AGENTS.md](src/AGENTS.md), [src/test/AGENTS.md](src/test/AGENTS.md), [src/test/java/momzzangseven/mztkbe/integration/e2e/AGENTS.md](src/test/java/momzzangseven/mztkbe/integration/e2e/AGENTS.md)를 확인합니다.

AI Agent는 작업 결과를 설명할 때 아래 내용을 남기는 것을 권장합니다.

- 확인한 사람용 문서
- 확인한 `AGENTS.md` 범위
- 변경한 파일과 변경 이유
- 실행한 검증 명령
- 검증하지 못한 항목
- 보안상 문서에 남기지 않은 값이나 절차가 있다면 그 사실

---

## 4. 로컬 개발 시작

### 4.1 필수 도구

- JDK 21
- Docker Desktop
- Git
- IDE 또는 editor

### 4.2 최초 1회 세팅

처음 합류한 개발자는 Git hook을 먼저 설치합니다.

```bash
./install-git-hooks.sh
```

Claude Code나 Codex CLI를 함께 사용한다면 Agent/Skill link도 준비합니다.

```bash
python3 scripts/agents/setup-skill-links.py
```

환경 변수는 내부 공유 기준을 따릅니다. `.env.example`은 필요한 key를 확인하고 CI에서 누락 여부를 검증하기 위한 파일입니다. 실제 값은 문서나 PR에 남기지 않습니다.

### 4.3 서버 실행

```bash
docker compose up -d
./gradlew bootRun
```

`docker-compose.yml`은 로컬 PostgreSQL/PostGIS를 실행합니다. 기본 개발 profile은 `dev`입니다.

### 4.4 확인 URL

| 용도 | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health check | `http://localhost:8080/actuator/health` |
| Prometheus metrics | `http://localhost:8080/actuator/prometheus` |

### 4.5 서버 중지

```bash
docker compose down
```

DB volume까지 지워야 할 때만 `docker compose down -v`를 사용합니다.

---

## 5. 개발 흐름

사람 개발자는 아래 순서로 진행합니다.

1. 작업 범위를 정합니다.
2. 관련 사람용 문서를 확인합니다.
3. 레이어, 패키지, 모듈 간 호출이 바뀐다면 `docs.shared/ARCHITECTURE.md`를 확인합니다.
4. DB와 외부 시스템 작업이 함께 일어난다면 `docs.shared/EXTERNAL_SYSTEM_SYNC.md`를 확인합니다.
5. 기존 모듈의 controller, use case, service, adapter, test 패턴을 먼저 따라갑니다.
6. 필요한 테스트를 추가하거나 기존 테스트를 수정합니다.
7. PR 전에 로컬 검증과 CI 보안 체크 기준을 확인합니다.

AI Agent가 작업한다면 위 흐름에 더해 가장 가까운 범위의 `AGENTS.md`를 먼저 읽고, 해당 지시가 사람용 문서와 충돌하지 않는지 확인합니다.

---

## 6. 실행·빌드·검증 명령

| 명령 | 용도 |
|---|---|
| `./gradlew bootRun` | 로컬 서버 실행 |
| `./gradlew clean bootJar` | JAR 빌드 |
| `./gradlew test` | unit/integration 테스트 |
| `./gradlew e2eTest` | E2E 테스트 |
| `./gradlew test e2eTest` | 전체 테스트 |
| `./gradlew spotlessApply` | Java format 적용 |
| `./gradlew spotlessCheck` | Java format 검증 |
| `./gradlew checkstyleMain` | main source Checkstyle 검증 |
| `./gradlew jacocoTestReport` | coverage report 생성 |

PR 전에는 최소한 아래 명령을 실행합니다.

```bash
bash scripts/ci/check-env-coverage.sh
./gradlew spotlessCheck
./gradlew checkstyleMain
./gradlew test
```

E2E, Playwright, 외부 연동 검증이 필요한지는 변경 범위와 기존 테스트 패턴을 기준으로 판단합니다. AI Agent가 판단하는 경우에는 `src/test/AGENTS.md`와 관련 모듈의 `AGENTS.md`도 함께 확인합니다.

---

## 7. 아키텍처 기준

아키텍처 규칙의 기준 문서는 [docs.shared/ARCHITECTURE.md](docs.shared/ARCHITECTURE.md)입니다.

DEV.md에는 세부 package tree나 예외 규칙을 반복해서 적지 않습니다. 설계하거나 리뷰할 때는 반드시 `docs.shared/ARCHITECTURE.md`를 기준으로 판단합니다.

빠르게 확인할 원칙은 다음과 같습니다.

- Controller는 use case interface를 호출합니다.
- Application service는 `api`와 `infrastructure`에 의존하지 않습니다.
- Domain은 Spring, JPA, Web3j, AWS SDK 같은 framework에 의존하지 않습니다.
- Infrastructure adapter가 output port를 구현합니다.
- 모듈 간 호출은 `docs.shared/ARCHITECTURE.md`의 cross-module 규칙을 따릅니다.
- `web3/shared` 예외는 `docs.shared/ARCHITECTURE.md`에 정의된 범위에서만 허용합니다.

새 예외가 필요하다면 DEV.md에만 적지 않습니다. 먼저 `docs.shared/ARCHITECTURE.md`에 근거와 허용 범위를 남깁니다.

---

## 8. 외부 시스템 동기화 기준

DB transaction과 외부 시스템 작업을 함께 다루는 기능은 [docs.shared/EXTERNAL_SYSTEM_SYNC.md](docs.shared/EXTERNAL_SYSTEM_SYNC.md)를 따릅니다.

DEV.md에는 외부 시스템별 세부 절차나 민감한 초기화 값을 적지 않습니다. 개발자는 구현 전에 아래 질문을 먼저 확인합니다.

- DB commit 전후로 외부 작업이 발생하는가?
- 실패했을 때 DB 상태와 외부 시스템 상태가 어긋날 수 있는가?
- retry, recovery, audit, scheduler가 필요한 흐름인가?
- 기존 모듈에 같은 문제를 해결한 패턴이 있는가?

하나라도 해당한다면 구현 전에 `docs.shared/EXTERNAL_SYSTEM_SYNC.md`를 기준으로 설계를 확인합니다.

---

## 9. 테스트 기준

사람 개발자는 이 문서의 테스트 요약, `src/test/java/momzzangseven/mztkbe/README.md`, 기존 테스트 코드를 먼저 봅니다. AI Agent는 추가로 `src/test/AGENTS.md`와 E2E 범위의 `AGENTS.md`를 확인합니다.

기본 원칙은 다음과 같습니다.

- `./gradlew test`는 unit/integration 테스트를 실행합니다.
- E2E 테스트는 별도의 실행 조건과 격리 규칙을 따릅니다.
- E2E class, cleanup, seed/reference table 처리는 기존 E2E 테스트 패턴을 따릅니다.
- Playwright는 외부 연동 시나리오가 필요할 때 사용합니다.
- 실제 secret, 실제 자산이 있는 지갑, 운영 credential은 테스트에 사용하지 않습니다.

테스트 실패를 수정할 때는 테스트 코드만 맞추지 않습니다. 관련 domain rule과 architecture 문서를 함께 확인합니다.

---

## 10. CI와 보안 점검

GitHub Actions의 기본 CI는 아래 항목을 확인합니다.

- `.env.example` coverage
- Spotless format
- Checkstyle
- Unit/integration test

보안 점검은 별도 workflow로 실행됩니다.

| Workflow | 실행 시점 | 목적 |
|---|---|---|
| `.github/workflows/security-gitleaks.yml` | `main`, `develop` 대상 push/PR | secret, token, key 유출 탐지 |
| `.github/workflows/semgrep.yml` | `main`, `develop` 대상 PR | 보안 취약 패턴 정적 분석 |

PR에서는 특히 아래 항목을 주의합니다.

- `.env`, private key, wallet secret, AWS credential, RPC secret을 커밋하지 않습니다.
- 로그, 테스트 fixture, 문서, PR 본문에 실제 secret 값을 남기지 않습니다.
- `.env.example`에는 실제 값이 아니라 필요한 key만 반영합니다.
- 보안 스캔 실패를 우회하지 않습니다. 원인을 제거한 뒤 다시 검증합니다.
- 외부 연동 테스트 결과를 공유할 때도 민감한 값은 masking합니다.

---

## 11. Agent와 Skill 기준

이 섹션은 AI Agent 또는 Claude/Codex 환경을 관리하는 개발자에게만 해당합니다.

Agent, Skill, config 운영 기준은 [docs.shared/AGENT_CONFIG.md](docs.shared/AGENT_CONFIG.md)를 따릅니다.

핵심 원칙은 다음과 같습니다.

- `AGENTS.md`는 AI context의 기준 문서입니다.
- 공유 skill은 `.agents/skills/<name>/SKILL.md`를 기준으로 관리합니다.
- `.claude/skills`는 환경별 link입니다.
- `CLAUDE.md` wrapper는 직접 수정하지 않습니다.
- Claude/Codex permission, hook, skill 정책을 바꾼다면 `docs.shared/AGENT_CONFIG.md`를 함께 확인합니다.

작업별 Skill 선택은 현재 session에서 제공되는 Skill 설명과 `.agents/skills/<name>/SKILL.md`를 기준으로 합니다.

---

## 12. Git과 PR

Branch와 commit 규칙은 현재 [docs.shared/AGENTS.md](docs.shared/AGENTS.md)에 정리되어 있습니다. 사람 개발자는 Git 규칙을 확인할 때만 해당 문서를 참조하면 됩니다.

PR을 올리기 전에 아래를 확인합니다.

- 관련 사람용 문서를 확인했습니다.
- 필요한 경우 `docs.shared/ARCHITECTURE.md`를 확인했습니다.
- 필요한 경우 `docs.shared/EXTERNAL_SYSTEM_SYNC.md`를 확인했습니다.
- AI Agent가 작업했다면 관련 범위의 `AGENTS.md`를 확인했습니다.
- 새 환경 변수 key는 `.env.example`에 반영했습니다.
- 민감한 값은 어떤 파일에도 남기지 않았습니다.
- 필요한 테스트를 실행했습니다.
- CI와 보안 체크 실패 가능성을 확인했습니다.
- API 변경이 있다면 FE 공유용 문서를 갱신했습니다.

---

## 13. 문서 수정 원칙

문서가 서로 충돌하지 않도록 아래 기준을 따릅니다.

- 개발 진입점은 `DEV.md`에 둡니다.
- 신규 합류와 초기 세팅은 `ONBOARDING.md`에 둡니다.
- 운영 절차는 `PROD.md`에 둡니다.
- 아키텍처 규칙은 `docs.shared/ARCHITECTURE.md`에 둡니다.
- DB와 외부 시스템 동기화 규칙은 `docs.shared/EXTERNAL_SYSTEM_SYNC.md`에 둡니다.
- Agent/Skill/config 정책은 `docs.shared/AGENT_CONFIG.md`에 둡니다.
- AI Agent 지시는 범위별 `AGENTS.md`에 둡니다.
- 개인 메모와 티켓별 초안은 `docs.local/`에 둡니다.

DEV.md를 수정할 때는 세부 규칙을 다시 적기보다 기준 문서로 연결합니다. 사람이 읽을 문서와 AI Agent가 읽을 문서는 구분해서 안내합니다.

---

## 14. 최종 체크리스트

기능 개발을 마치기 전에 아래를 확인합니다.

- [ ] 작업 범위에 맞는 사람용 문서를 확인했다.
- [ ] AI Agent가 작업했다면 관련 `AGENTS.md`를 확인했다.
- [ ] 아키텍처 판단이 필요해 `docs.shared/ARCHITECTURE.md`를 확인했다.
- [ ] 외부 시스템 작업이 있어 `docs.shared/EXTERNAL_SYSTEM_SYNC.md`를 확인했다.
- [ ] 새 환경 변수 key를 `.env.example`에 반영했다.
- [ ] 민감한 값을 commit, log, test fixture, 문서, PR 본문에 남기지 않았다.
- [ ] 필요한 테스트를 실행했다.
- [ ] `spotlessCheck`, `checkstyleMain`, `test`가 통과한다.
- [ ] 보안 스캔 실패 가능성을 확인했다.
- [ ] API 변경이 있다면 FE 공유용 문서를 갱신했다.
