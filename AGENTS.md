# AGENTS.md

> 본 파일은 Claude Code, Codex CLI, 향후 도입될 모든 AI 에이전트의 단일 진본입니다.
> 같은 디렉토리의 `CLAUDE.md` 는 `@AGENTS.md` 만 포함한 import wrapper 이므로 직접 수정 금지.
> 도구 간 skill / config 동기화 정책은 `docs.shared/AGENT_CONFIG.md` 참조.
> 새 팀원이라면 루트 `ONBOARDING.md` 를 먼저 읽으세요.

This file provides guidance to AI agents (Claude Code, Codex CLI; other tools may also load AGENTS.md) when working with code in this repository.

## Project Overview
MZTK-BE is a backend for a workout verification and community platform with Web3 token rewards. Web3 features interact with smart contracts on the Optimism network.

- Users earn XP through in-app activity (workout verification, community participation); accumulating enough XP triggers a level-up.
- On level-up, ERC-20 tokens are distributed to the user's registered Web3 wallet.
- Workout verification: location check-in, workout photo upload, third-party app screenshot.
- Community: free board + Q&A board (question poster stakes tokens; accepted answerer receives them).
- Marketplace: spend ERC-20 tokens to buy PT sessions; only `TRAINER` role can list classes.
- Infrastructure: AWS EC2, RDS, S3, Lambda.

## Session Efficiency Rules

**Context management:**
- When cumulative context exceeds 200k tokens, run `/compact` before proceeding.
- After reading a file larger than 500 lines, run `/compact` if context is already large.
- Check file size with `wc -l` before reading unfamiliar files; use `offset`/`limit` to read only the relevant section.
- Start a new session for unrelated tasks — AGENTS.md and MEMORY.md carry context across sessions without cost.

## Critical Rules

**Testing:** → full rules in `src/test/AGENTS.md`
- Before PR, must pass every test.
- Unit/integration tests use H2; E2E tests tagged `@Tag("e2e")` run against live DB.
- `./gradlew test` excludes E2E; `./gradlew e2eTest` runs only E2E.

**CI enforcement (must pass locally before push):**
1. `spotlessCheck` — Google Java Format via Spotless
2. `checkstyleMain` — Checkstyle with `config/checkstyle/google_checks.xml`
3. `test` — unit/integration tests

**Branch strategy:**
1. `feat//[JIRA ticket]-xxx-...` → PR to `develop` → PR to `main` (triggers prod deploy)

## Commands

```bash
./gradlew bootRun           # Run application (dev profile)
./gradlew clean bootJar     # Build JAR
./gradlew test              # Unit/integration tests (excludes E2E)
./gradlew e2eTest           # E2E tests only (requires live PostgreSQL)
./gradlew test e2eTest      # All tests
./gradlew spotlessApply     # Apply code formatting (must pass before commit)
./gradlew spotlessCheck     # Check formatting without fixing
./gradlew checkstyleMain    # Run Checkstyle
./gradlew jacocoTestReport  # Generate coverage report (build/reports/jacoco/test/html/)
```

## Architecture

**Stack:** Spring Boot 3.4 · Java 21 · PostgreSQL · JPA/QueryDSL · JWT + OAuth2 · AWS S3 · Web3j

**Package layout:** `momzzangseven/mztkbe/`
- `modules/` — business modules
- `global/` — cross-cutting concerns (audit, config, error, response, security, time, util)

→ Read `docs.shared/ARCHITECTURE.md` before designing or reviewing code.
→ Production code patterns, DB profiles, Security details: `src/main/AGENTS.md`
→ Testing conventions & E2E rules: `src/test/AGENTS.md`
→ Coding conventions & commit types: `docs.shared/AGENTS.md`

@AGENTS.local.md
