# src/main — Production Code Guide

Read `docs.shared/ARCHITECTURE.md` before designing or modifying production code.
Read `docs.shared/EXTERNAL_SYSTEM_SYNC.md` before designing or modifying any flow that mutates
both the DB and an external system (AWS KMS, S3, on-chain RPC, third-party APIs).

ARCHITECTURE.md 의 layer dependency rule 위반은 `.claude/hooks/check-architecture-rules.py`
hook 이 PostToolUse 시점에 정적 분석으로 잡아 메시지를 띄운다 (informational, 차단은 아님).

## Key Patterns

**Exception Handling**
- Business exceptions: Derive from `BusinessException`, use `ErrorCode`
- Domain-specific exceptions: e.g. `Web3InvalidInputException`
- Global exception handler (`GlobalExceptionHandler`) converts to `ApiResponse`
- Client input errors: `IllegalArgumentException` → HTTP 400

**API Response**
- Standard wrapper: `ApiResponse<T>`
- Success: `ApiResponse.success(data)` or `ApiResponse.success(message, data)`
- Failure: `ApiResponse.error(message, code)` (use ErrorCode.code)

## Database Profiles

- **Dev:** Hibernate `ddl-auto: update`, Flyway disabled
- **Integration** (E2E + `MigrationValidationTest`): real PostgreSQL via `DB_URL_E2E`,
  Flyway `validate-on-migrate=true`, `baseline-on-migrate=false`, `ddl-auto=validate`.
  The DB must be empty on first run — never reuse `mztk_dev`.
- **Prod:** `ddl-auto: none`, Flyway enabled with validation
- Migrations: `src/main/resources/db/migration/V*.sql` — never modify existing migration files

## Security

- JWT lifecycle managed in `global/security/`
- OAuth2 providers: Kakao, Google
- Web3 auth: EIP-4361 login challenges, EIP-712 typed data signing, EIP-7702 tx sponsoring

@AGENTS.local.md
