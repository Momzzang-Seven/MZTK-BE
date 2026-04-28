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

## Seed-Admin Bootstrap (opt-in)

OFF by default. Enable: `@TestPropertySource(properties="mztk.admin.bootstrap.enabled=true")` + override `requiresBootstrapSeedAdmins()` returning `true`.

## Rules

- No `@AfterEach` cleanup — `DatabaseCleaner` handles it automatically.
- No `@Transactional` on E2E classes — real HTTP commits in its own TX; rollback masks bugs.
- No parallel forks — `DatabaseCleaner` is not concurrency-safe across forks.
- No `@SpringBootTest`/`@Tag`/`@ActiveProfiles` on subclasses — base provides them.
- New seed/reference tables → add to `DatabaseCleaner.EXCLUDED_TABLES`.
