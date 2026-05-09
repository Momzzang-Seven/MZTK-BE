# src/test — Test Code Guide

## Running Tests

```bash
./gradlew test          # Unit/integration (H2, excludes @Tag("e2e"))
./gradlew e2eTest       # E2E only (requires live PostgreSQL via DB_URL_E2E)
./gradlew test e2eTest  # All tests
```

## Unit / Integration Tests

- Use H2 in-memory database — no external dependencies needed.

## E2E Tests (`integration/e2e/`)

- All E2E tests must extend `E2ETestBase` — provides `@Tag("e2e")`, `@SpringBootTest`, `DatabaseCleaner`.
- Never write `@AfterEach` cleanup — `DatabaseCleaner` handles per-table teardown automatically.
- Point `DB_URL_E2E` at a dedicated, empty Postgres DB (e.g. `mztk_e2e`) — never `mztk_dev`.

## `MigrationValidationTest` — intentionally NOT `@Tag("e2e")`

Runs on every `./gradlew test` against real PostgreSQL (`integration` profile).
Forces Flyway `validate-on-migrate` + Hibernate `ddl-auto=validate` — any migration/entity drift
fails CI immediately. Requires `DB_URL_E2E` pointing at a dedicated, empty Postgres DB.

@AGENTS.local.md
