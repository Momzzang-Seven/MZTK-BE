# Integration / E2E Test Guide

This document describes conventions for writing E2E tests under
`src/test/java/momzzangseven/mztkbe/integration/e2e/**`. Read this before adding
or modifying any `@Tag("e2e")` test.

## Running

- `./gradlew test` — unit/integration tests only (excludes `@Tag("e2e")`).
- `./gradlew e2eTest` — E2E tests only. Requires a **dedicated** PostgreSQL
  database pointed to by `DB_URL_E2E` (never reuse `mztk_dev`; Flyway owns the
  schema and will conflict with `ddl-auto: update`).
- `./gradlew test e2eTest` — run everything.

## Profile & Configuration

- Profile: `integration` (see `src/test/resources/application-integration.yml`).
- Flyway: `enabled=true`, `validate-on-migrate=true`, `clean-disabled=false`.
- JPA: `ddl-auto=validate` — Flyway owns the schema; Hibernate only verifies.
- AWS: real S3Client beans are built with fake test credentials. Adapter-level
  stubs exist for admin delivery (`LogBootstrapDeliveryAdapter`) and recovery
  anchor (`PropertiesRecoveryAnchorAdapter`), both gated to `@Profile("!prod")`.
  **S3 presign/delete/verification adapters are NOT profile-gated yet** — avoid
  exercising code paths that make real S3 calls until that refactor lands.

## Base Class: `E2ETestBase`

Located at `integration/e2e/support/E2ETestBase.java`. Every E2E test **must**
extend it. It provides:

- `@Tag("e2e")`, `@ActiveProfiles("integration")`,
  `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- Default `mztk.admin.bootstrap.enabled=false` — seed-admin bootstrap is OFF by
  default so tests get a clean user table.
- Automatic `@AfterEach` DB cleanup via `DatabaseCleaner`.
- Opt-in seed-admin re-provisioning hook (`requiresBootstrapSeedAdmins()`).
- Auto-injected fields: `port` (`@LocalServerPort`), `restTemplate`
  (`TestRestTemplate`), `objectMapper` (`ObjectMapper`). Subclasses should
  **not** redeclare these — just use them directly.
- Shared HTTP/auth helpers (see "Shared HTTP & auth helpers" below) for the
  signup → login → access-token flow that nearly every E2E test needs.

### Minimal example

```java
@DisplayName("[E2E] Something")
class SomethingE2ETest extends E2ETestBase {
  @Autowired private JdbcTemplate jdbcTemplate;
  // ... test methods; no @AfterEach cleanup needed.
}
```

### Adding per-test properties

Subclass `@TestPropertySource` is **merged** with the base's, so you only
specify extras:

```java
@TestPropertySource(
    properties = {
      "mztk.admin.recovery.anchor=test-e2e-recovery-anchor",
      "mztk.admin.seed.seed-count=2"
    })
class MyTest extends E2ETestBase { ... }
```

## Shared HTTP & auth helpers

`E2ETestBase` exposes a small toolkit so individual tests don't reimplement
the signup/login boilerplate. Use these instead of writing your own
`@LocalServerPort` / `TestRestTemplate` / `signup()` / `login()` per file.

### Auto-injected fields (already wired by the base)

- `protected int port` — `@LocalServerPort`.
- `protected TestRestTemplate restTemplate`.
- `protected ObjectMapper objectMapper`.
- `protected static final String DEFAULT_TEST_PASSWORD = "Test@1234!"`.

### Helper methods

| Method | Purpose |
|---|---|
| `baseUrl()` | Returns `http://localhost:{port}`. |
| `randomEmail()` | Generates a UUID-based unique `@test.com` email. |
| `bearerJsonHeaders(token)` | `HttpHeaders` with `Content-Type: application/json` + `Bearer {token}`. |
| `jsonOnlyHeaders()` | JSON `HttpHeaders` with no auth (anonymous flows). |
| `signupUser(email, pw, nickname) → Long` | `POST /auth/signup`; returns the new `userId` from `/data/userId`. |
| `loginUser(email, pw) → String` | `POST /auth/login` with `provider=LOCAL`; returns `accessToken` from `/data/accessToken`. |
| `signupAndLogin(nickname) → TestUser` | Generates an email + uses `DEFAULT_TEST_PASSWORD`, then signup + login. The "just give me an authenticated user" shortcut. |
| `signupAndLogin(email, pw, nickname) → TestUser` | Same, but with explicit credentials. |

`TestUser` is a `record(Long userId, String email, String password, String accessToken)` —
pick the field you need (`.userId()`, `.accessToken()`, …).

Helper methods convert `JsonProcessingException` to unchecked, so callers
**don't** need `throws Exception` just to invoke them.

### Naming note

The helpers are named `signupUser` / `loginUser` / `randomEmail` /
`bearerJsonHeaders` / `jsonOnlyHeaders` (instead of the more obvious
`signup` / `login` / `uniqueEmail` / `bearerHeaders` / `jsonHeaders`) on
purpose: several existing tests still declare their own `private` helpers
with those shorter names, and Java forbids a subclass from redeclaring an
inherited `protected` method with weaker (`private`) visibility. The unique
prefixes let new and old code coexist without forcing a sweeping refactor.

### Usage examples

Tests that just need an authenticated user:

```java
class MyE2ETest extends E2ETestBase {
  private String accessToken;

  @BeforeEach
  void setUp() {
    accessToken = signupAndLogin("my-nickname").accessToken();
  }

  @Test
  void doesSomething() {
    restTemplate.exchange(
        baseUrl() + "/some/endpoint",
        HttpMethod.GET,
        new HttpEntity<>(bearerJsonHeaders(accessToken)),
        String.class);
  }
}
```

Tests that need the `userId` (e.g. to seed FK-bound rows directly via
`JpaRepository` or `JdbcTemplate`):

```java
@BeforeEach
void setUp() {
  userId = signupAndLogin("worker").userId();
}
```

Tests that need multiple distinct users (forbidden checks, ownership tests,
multi-actor flows):

```java
TestUser owner = signupAndLogin("owner");
TestUser other = signupAndLogin("other");
// owner.accessToken() vs other.accessToken()
```

Tests that need explicit credentials (e.g. password-based step-up,
re-login after a password change):

```java
String email = randomEmail();
signupUser(email, DEFAULT_TEST_PASSWORD, "step-up-user");
String token = loginUser(email, DEFAULT_TEST_PASSWORD);
```

**Don't** redeclare `port`, `restTemplate`, or `objectMapper` in subclasses —
the base already provides them. Adding `@LocalServerPort private int port`
to a subclass shadows the base field and makes refactors confusing.

## `DatabaseCleaner`

Located at `integration/e2e/support/DatabaseCleaner.java`. Gated to
`@Profile("integration")`. On startup it queries `information_schema.tables`
for all `public` schema tables and caches a single
`TRUNCATE TABLE t1, t2, ... RESTART IDENTITY CASCADE` statement.

### Why TRUNCATE ... CASCADE?

- PostgreSQL resolves FK ordering automatically — no per-table DELETE ordering.
- `RESTART IDENTITY` resets sequences so tests get deterministic IDs.
- Single statement in one transaction — fast, atomic.

### Tables excluded from truncation (preserved across tests)

Edit `DatabaseCleaner.EXCLUDED_TABLES` if you add a new seed/reference table:

- `flyway_schema_history` — Flyway metadata.
- `level_policies`, `xp_policies` — XP/level reference data (loaded from
  `src/main/resources/db/seed/`).
- `web3_treasury_keys` — Web3 treasury seed.

**Rule:** anything loaded once at app-startup (not by tests) belongs in the
excluded set. If a test needs to mutate one of these tables, it must restore
the row itself — the cleaner will not touch it.

## Seed-Admin Bootstrap (Opt-In)

`SeedAdminBootstrapper` is an `ApplicationRunner` that inserts N `ADMIN_SEED`
rows into `users` + `admin_accounts` at context startup. It's gated by
`mztk.admin.bootstrap.enabled` (default `true` in app, forced `false` by
`E2ETestBase`).

The cleaner wipes `users` and `admin_accounts` on every `@AfterEach`, so
tests that rely on the bootstrap rows must **re-provision them after cleanup**.
There are three steps:

1. Enable the property in your subclass:

   ```java
   @TestPropertySource(properties = "mztk.admin.bootstrap.enabled=true")
   ```

2. Override `requiresBootstrapSeedAdmins()`:

   ```java
   @Override
   protected boolean requiresBootstrapSeedAdmins() { return true; }
   ```

3. (If required) also set `mztk.admin.recovery.anchor` — the bootstrapper
   fails fast without it.

With all three in place:
- Startup: `SeedAdminBootstrapper` inserts the initial rows.
- After each test: `DatabaseCleaner.clean()` wipes them, then
  `DatabaseCleaner.reseedBootstrapAdmins()` re-runs the idempotent
  `BootstrapSeedAdminsUseCase`, restoring them for the next test.

Tests that create their own admin rows directly (via JDBC INSERT) do **not**
need this — they just let the cleaner truncate and re-insert in the next
test's arrange phase. See `AdminReseedLoginConcurrencyE2ETest` for that
pattern.

## Dos and Don'ts

**Do:**
- Extend `E2ETestBase`. Never re-declare `@SpringBootTest` / `@ActiveProfiles`
  / `@Tag("e2e")` on the subclass — the base has them.
- Trust the cleaner. Remove any `@AfterEach` that does manual DELETEs.
- Use `TestRestTemplate` for HTTP-level flows; `JdbcTemplate` is fine for
  arrange-phase setup when going through the API would be noisy.
- Add new seed/reference tables to `DatabaseCleaner.EXCLUDED_TABLES` in the
  same commit that introduces them.

**Don't:**
- Don't add `@Transactional` to E2E test classes — real HTTP requests commit
  in their own transactions, so a class-level rollback is a lie and will mask
  bugs.
- Don't point `DB_URL_E2E` at a shared/dev database. The cleaner TRUNCATEs
  the entire `public` schema (minus exclusions) on every test.
- Don't run E2E in parallel (`maxParallelForks > 1`). Tests share a single DB;
  the cleaner is not concurrency-safe across forks.
- Don't call real AWS from a new code path without first adding a
  profile-gated stub adapter. The current AWS isolation is partial.
- Don't modify existing Flyway migration files (`V*.sql`). Add a new version.
