# 테스트 작성 가이드

AI 에이전트 및 팀원용 테스트 코드 작성 가이드.

→ E2E 상세 규칙: `java/momzzangseven/mztkbe/integration/e2e/CLAUDE.md`

## 디렉터리 구조

```
momzzangseven.mztkbe (test root)
│
├── integration/
│   ├── e2e/              ← Java: Local server + Local DB 통합 테스트 (@Tag("e2e"))
│   │   └── {기능명}E2ETest.java
│   └── play_wright/      ← TypeScript: 외부 API 실연동 E2E (Java 없음)
│       └── {기능명}/
│           ├── {기능명}.spec.ts      ← Playwright 시나리오
│           └── {기능명}-report.md    ← 실행 결과 보고서
│
└── modules/
    └── {모듈명}/
        ├── api/          ← 전체 통합 테스트 (MockMVC + H2)
        ├── application/  ← Service/DTO 단위 테스트 (Mockito)
        ├── domain/       ← 도메인 모델/VO 단위 테스트
        └── infrastructure/ ← Adapter 단위 테스트 (Mockito)
```

## 테스트 유형별 목적

| 테스트 유형 | 목적 | 핵심 어노테이션 |
|------------|------|----------------|
| `integration/e2e/` | HTTP → DB 전체 플로우 (외부 API는 `@MockitoBean`) | `E2ETestBase` 상속 필수 |
| `integration/play_wright/` | 카카오/Google 등 실외부 API 포함 E2E | TypeScript + Playwright |
| `modules/.../api/` | MockMVC + H2, Security 필터 포함 | `@SpringBootTest`, `@Transactional` |
| `modules/.../application/` | Service 비즈니스 로직 (Port Mock) | `@ExtendWith(MockitoExtension.class)` |
| `modules/.../domain/` | 도메인 규칙 (의존성 없음) | 순수 JUnit5 |
| `modules/.../infrastructure/` | Adapter 변환/위임 (JPA Mock) | `@ExtendWith(MockitoExtension.class)` |

## 파일명 규칙

- Contract 통합 테스트: `{기능명}ControllerTest.java`
- 통합 테스트: `{기능명}IntegrationTest.java`
- Playwright 보고서: `{기능명}-report.md` (실행 후 반드시 저장)

## 패키지 예시 (실제 파일)

- application: `modules/account/application/delegation/RefreshTokenValidatorTest.java`
- application: `modules/web3/transfer/application/service/RegisterQuestionRewardIntentServiceTest.java`
- domain/event: `modules/web3/transaction/domain/event/Web3TransactionSucceededEventTest.java`
- infrastructure/event: `modules/location/infrastructure/event/LocationUserSoftDeleteEventHandlerTest.java`
- infrastructure/scheduler: `modules/web3/execution/infrastructure/scheduler/ExecutionIntentCleanupSchedulerTest.java`

## 실행 명령어

```bash
./gradlew test                         # 단위 + MockMVC + H2 통합 (E2E 제외)
./gradlew e2eTest                      # E2E only (DB_URL_E2E 환경변수 필요)
./gradlew test e2eTest                 # 전체
./gradlew test --tests "*.XxxTest"    # 특정 테스트 클래스
npx playwright test                    # Playwright (play_wright/ 디렉터리에서)
```

## Playwright 설정

`play_wright/` 디렉터리에서 `npm ci` 후 `npx playwright install chromium`.
`.env`에 `BACKEND_URL`, `TEST_WALLET_ADDRESS`, `TEST_PRIVATE_KEY` 설정 (`.gitignore` 필수).
⚠️ `TEST_PRIVATE_KEY`는 실제 자산이 있는 지갑 키 절대 사용 금지 — 테스트 전용 빈 지갑만 사용.
AWS Lambda 연동 테스트 시 `ngrok http 8080` 후 forwarding URL을 Lambda `SPRING_BE_BASE_URL`에 입력.
로컬 E2E DB 셋업/재생성 절차: `docs/MOM-339/README.md` 참고.
