---
name: generate-test-cases
description: Generates comprehensive test case documentation for implemented code. Use this skill whenever the user asks to generate test cases, create a test plan, document tests based on code, or says things like "구현된 코드를 바탕으로 테스트를 생성해줘", "테스트 케이스 만들어줘", "테스트 문서 작성해줘", "테스트 시나리오 작성해줘", "test cases for this feature", or "write test documentation". This skill should be triggered proactively whenever the user has just finished implementing a feature and needs test coverage documented.
---

# Generate Test Cases

Generates a comprehensive test case document for the implemented code on the current git branch.
The output document covers three layers: **Module Test**, **E2E Test**, and **Playwright Test**.

---

## Step 1 — Identify the scope

1. Run `git branch --show-current` to get the current branch name. Then sanitize it for use as a directory/file name by replacing `/` with `-` (call the sanitized result `<branch-name>`). For example, `feature/MOM-304-get-image-information` becomes `feature-MOM-304-get-image-information`.
2. Determine which files were changed in this branch relative to the base branch (`develop` or `main`):
   ```bash
   git diff --name-only develop...HEAD
   ```
   If `develop` doesn't exist, fall back to `main`.
3. Read every changed file to understand what was implemented. Focus on:
   - New or modified API controllers (request/response shape, HTTP method, path, auth requirements)
   - Use case interfaces and their input/output types
   - Service logic (business rules, validations, error paths)
   - Domain model changes (new fields, state transitions, invariants)
   - Output port interfaces (what external systems are called)
   - Infrastructure adapters (DB queries, S3 operations, Web3 calls, event publishing)

4. Also read the relevant existing tests in `src/test/` for the changed modules to understand current patterns and avoid duplicating coverage that already exists.

---

## Step 2 — Classify test cases by layer

Use these definitions consistently:

| Layer | When to use | Environment |
|-------|-------------|-------------|
| **Module Test** | Testing a single class or small unit in isolation; mocks all dependencies | H2 in-memory DB, all ports mocked |
| **E2E Test** | Testing the full request→response flow through the real running server; no external SaaS | Local server + local PostgreSQL DB |
| **Playwright Test** | Testing scenarios that involve an actual browser UI or real third-party API integrations (S3, Web3 wallet, OAuth2) | Local server + real external systems |

**Decision guide:**
- Pure service/domain logic with no external calls → Module Test
- REST endpoint behavior, DB persistence, transaction rollback, concurrent access → E2E Test
- Browser-driven flows (OAuth login, MetaMask signing, file upload via presigned URL) → Playwright Test

---

## Step 3 — Generate test cases

For each layer, generate test cases following the rules below. Be exhaustive — it is better to have too many cases than too few.

### Happy day cases
Cover every successfully completed user journey:
- All supported input combinations
- Boundary values that are still valid (e.g., exactly max count, minimum required fields)
- Responses with correct HTTP status, body shape, and side effects (DB state, events published)

### Edge cases
Cover failure modes and boundary conditions:
- Invalid input (null, empty, wrong type, out of range, unsupported enum values)
- Business rule violations (exceeding limits, forbidden state transitions)
- Missing or expired auth tokens
- Resource not found (entity with given ID does not exist)
- Concurrent access: what happens if two requests race to modify the same record
- Idempotency: calling the same operation twice
- Partial failures (e.g., DB write succeeds but downstream event fails)

### Transaction & DB cases (include in E2E layer)
Always consider:
- **Rollback**: when the service throws an exception mid-operation, verify no partial writes persist
- **Transaction scope**: verify that changes made inside a nested method are committed or rolled back as expected
- **DB lock / optimistic locking**: if the entity uses `@Version` or explicit locks, test that concurrent updates are handled without data loss
- **Cascade effects**: deleting a parent entity cascades correctly (or does not, per the spec)

---

## Step 4 — Write the document

### Output path

```
/docs/test/<branch-name>/<branch-name>.md
```

If the file already exists, merge your new cases with the existing content — update outdated cases and append new ones. Do not delete existing cases unless they are clearly wrong or superseded.

### Document structure

```markdown
# Test Cases — <branch-name>

> Generated: <YYYY-MM-DD>
> Scope: <list the key files / features covered>

---

## 1. Module Test

> Environment: JUnit 5 + Mockito, H2 in-memory DB
> Run with: `./gradlew test`

### <ClassName> — <short description>

#### [M-1] <Case name>
**Target:** `<ClassName>#<methodName>`
**Setup:**
- <what to mock and what values to return>

**Input:** <describe input parameters or command object>

**Expected:**
- <what the return value / side effect should be>
- <which mock interactions should be verified>

**Why this matters:** <one sentence explaining what bug this would catch>

---
(repeat for each module test case)

---

## 2. E2E Test

> Environment: local Spring Boot server + local PostgreSQL
> Run with: `./gradlew e2eTest`
> Tag: `@Tag("e2e")`

### <Feature name>

#### [E-1] <Case name>
**HTTP:** `<METHOD> <path>`
**Auth:** <JWT required / anonymous>

**Request:**
```json
{ ... }
```

**Expected response:**
- HTTP `<status>`
- Body: `{ ... }`

**DB state after:** <what rows should exist or be modified>

**Why this matters:** <one sentence>

---
(repeat for each E2E case)

---

## 3. Playwright Test

> Environment: local Spring Boot server + real external systems (S3, MetaMask, OAuth2, etc.)
> Run with: Playwright test runner

### <Feature / integration name>

#### [P-1] <Case name>
**Preconditions:** <what must be set up — seeded DB rows, connected wallet, etc.>

**Steps:**
1. <user action>
2. <user action>
3. ...

**Expected:**
- <what the UI/API should show>
- <what side effects should have occurred in external systems>

**Why this matters:** <one sentence>

---
(repeat for each Playwright case)
```

### Formatting rules
- Use sequential IDs: `[M-1]`, `[M-2]`, `[E-1]`, `[E-2]`, `[P-1]`, etc.
- Write case names in English; descriptions and "Why this matters" may be in Korean if that matches the existing document style.
- Keep each case self-contained — a reader should be able to run it without reading other cases.
- For E2E cases that test transaction rollback or DB locking, explicitly state which assertions to run against the DB after the operation.
- Group cases by controller/service class within each layer section.

---

## Step 5 — Confirm and save

After writing the document:
1. Print a short summary to the user: how many cases were generated per layer and which files/features were covered.
2. If any area of the code had unclear behavior that you had to assume, call that out explicitly so the user can verify.
