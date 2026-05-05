# Integration / E2E Test Guide

**Run:** `./gradlew e2eTest` — requires `DB_URL_E2E` (dedicated Postgres, e.g. `mztk_e2e`; never `mztk_dev`).

## Profile

Profile: `integration` — Flyway `validate-on-migrate=true`, JPA `ddl-auto=validate`.
S3 presign/delete/verification adapters are **not** profile-gated — avoid new S3 code paths until that refactor lands.

## `E2ETestBase` — every test must extend

`support/E2ETestBase.java` provides `@Tag("e2e")`, `@SpringBootTest(RANDOM_PORT)`, `@ActiveProfiles("integration")`, auto-`DatabaseCleaner`.

**Auto-injected (don't redeclare in subclass):** `port`, `restTemplate`, `objectMapper`, `DEFAULT_TEST_PASSWORD = "Test@1234!"`.

| Helper | Returns |
|--------|---------|
| `signupAndLogin(nickname)` | `TestUser` — email auto-generated |
| `signupAndLogin(email, pw, nickname)` | `TestUser` — explicit credentials |
| `signupUser(email, pw, nickname)` | `Long` userId |
| `loginUser(email, pw)` | `String` accessToken |
| `randomEmail()` | UUID `@test.com` email |
| `bearerJsonHeaders(token)` | `HttpHeaders` auth + JSON |
| `jsonOnlyHeaders()` | `HttpHeaders` JSON (anonymous) |

`TestUser` is `record(Long userId, String email, String password, String accessToken)`.

> Prefix `signupUser`/`loginUser` not `signup`/`login` — subclass cannot redeclare `protected` method with `private` visibility.

## `DatabaseCleaner`

`TRUNCATE … RESTART IDENTITY CASCADE` after every `@AfterEach`. Excluded (preserved):
`flyway_schema_history`, `level_policies`, `xp_policies`, `web3_treasury_wallets`.
→ Add new seed tables to `DatabaseCleaner.EXCLUDED_TABLES` in the same commit.

> **Excluded 테이블에 INSERT 하는 테스트는 반드시 `@AfterEach` 에서 자기가 넣은 행을 명시적으로 삭제할 것.**
> 특히 `web3_treasury_wallets` 는 `wallet_alias` 가 unique 라서, 한 테스트가 시드한 행이 남아 있으면
> 다른 테스트의 `provision()` 흐름이 `TREASURY_003 (legacy address mismatch)` 또는
> `TREASURY_004 (ALREADY_PROVISIONED)` 로 오염된다.
> 예: `GetMyProfileE2ETest#cleanRewardTreasuryWalletSeed`,
> `TreasuryKeyLifecycleE2ETest#cleanTreasuryWalletRow`.

## Seed-Admin Bootstrap (opt-in)

OFF by default. Enable: `@TestPropertySource(properties="mztk.admin.bootstrap.enabled=true")` + override `requiresBootstrapSeedAdmins()` returning `true`.

## `ApiResponse` 응답 형식

모든 엔드포인트는 `ApiResponse<T>` 래퍼로 응답한다. JSON 바디의 `status` 필드 값은 다음 두 가지뿐이다.

| 상황 | `status` 값 |
|------|-------------|
| 성공 (`2xx`) | `"SUCCESS"` |
| 실패 (`4xx` / `5xx`) | `"FAIL"` |

> **`"ERROR"` 는 존재하지 않는다.** `ApiResponse.error(...)` 는 항상 `"FAIL"` 을 반환한다.

```java
// 올바른 단언
assertThat(body.at("/status").asText()).isEqualTo("FAIL");

// 잘못된 단언 — 절대 쓰지 말 것
assertThat(body.at("/status").asText()).isEqualTo("ERROR");  // ← 항상 실패
```

실패 응답의 전체 구조:

```json
{
  "status": "FAIL",
  "message": "...",
  "code": "TREASURY_001"
}
```

## Rules

- No `@AfterEach` cleanup — `DatabaseCleaner` handles it automatically.
  **예외:** `EXCLUDED_TABLES` 에 INSERT 한 테스트는 자기가 넣은 행을 `@AfterEach` 로 직접 삭제해야 함 (위 `DatabaseCleaner` 절 참고).
- No `@Transactional` on E2E classes — real HTTP commits in its own TX; rollback masks bugs.
- No parallel forks — `DatabaseCleaner` is not concurrency-safe across forks.
- No `@SpringBootTest`/`@Tag`/`@ActiveProfiles` on subclasses — base provides them.
- New seed/reference tables → add to `DatabaseCleaner.EXCLUDED_TABLES`.
