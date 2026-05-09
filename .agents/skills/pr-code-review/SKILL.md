---
name: pr-code-review
description: MZTK-BE project PR code review checklist and feedback format. Use when reviewing pull requests, examining code changes, or when the user asks for a code review.
---

# PR Code Review Best Practice (MZTK-BE)

## Purpose

Ensures a consistent standard and feedback format during MZTK-BE PR reviews. Checks Hexagonal Architecture, Java conventions, security, and performance.

## PR Description Retrieval

Before starting the review, check if there is an open PR for the current branch:

```bash
gh pr view --json title,body,number,url 2>/dev/null
```

- If an open PR exists: read the PR title and description to understand the intended scope, goals, and context of the changes. Use this information to verify that the actual code changes align with the stated objectives.
- If no open PR exists: skip this step and proceed directly to the review checklist using only the code diff.

## Review Checklist

### 1. Hexagonal Architecture Compliance
- For checking Hexagonal Architecture compliance, refer to ../hexagonal-architecture/SKILL.md.

- [ ] **Dependency Direction**: domain → (no dependencies), application → port(interface) only, infrastructure → port implementation
- [ ] **Domain Purity**: No framework dependencies (Spring, JPA, Web3j, etc.) in the domain package
- [ ] **Port Usage**: Services depend only on Port interfaces, do not directly reference Adapter classes
- [ ] **No Layer Mixing**: No business logic in Controller, Entity not exposed as Domain

### 2. Java & Project Convention
- For Java & project convention compliance, refer to ../java-best-practice/SKILL.md.

- [ ] **Domain Model**: `@Getter`, `@Builder(toBuilder=true)`, `@AllArgsConstructor(access=PRIVATE)`, `final` fields
- [ ] **Immutability**: When domain state changes, return a new instance via `toBuilder()`
- [ ] **DTO**: Command/Result as `record`, Request DTO uses `@Valid` + Bean Validation
- [ ] **Exceptions**: Business exceptions extend `BusinessException`, use `ErrorCode`
- [ ] **Naming**: Naming rules for UseCase, Port, Adapter, Service are followed

### 3. Testing

- [ ] **Tests exist** for new/changed logic
- [ ] **Unit tests**: Service uses Port mocks, Given-When-Then structure
- [ ] **DisplayName**: Use Korean scenario or `methodName_Condition_ExpectedResult` pattern
- [ ] **Include exception/boundary cases** in tests

### 4. Transaction Boundaries & DB Consistency

- [ ] **Transaction Scope**: `@Transactional` is placed at the service layer (application service), not at the port/adapter layer unless there is a clear reason
- [ ] **Transaction Propagation**: When a method calls another `@Transactional` method, verify propagation behavior (`REQUIRED`, `REQUIRES_NEW`, etc.) is intentional
- [ ] **Partial Commit Risk**: If multiple DB writes occur in one transaction, confirm that a failure in a later write does not leave the DB in an inconsistent intermediate state
- [ ] **Event Publishing Order**: Domain events published via `ApplicationEventPublisher` must be published *after* the DB write completes — ensure `@TransactionalEventListener(phase = AFTER_COMMIT)` is used when the listener must see the committed state
- [ ] **Read-Only Transactions**: `@Transactional(readOnly = true)` is applied to query-only methods to enable query optimizations and prevent accidental writes
- [ ] **Lazy Loading Boundary**: No lazy-loaded JPA associations accessed outside a transaction (guard against `LazyInitializationException` in adapters/controllers)
- [ ] **Optimistic/Pessimistic Locking**: Concurrent updates to shared aggregates (e.g. XP balance, token balance) use appropriate locking (`@Lock`, `@Version`) to prevent lost updates
- [ ] **Self-Invocation Trap**: `@Transactional` on a method called from within the same bean does not create a new proxy — verify the transactional semantics are actually applied
- [ ] **Exception Rollback Rules**: `@Transactional` only rolls back on unchecked exceptions by default; confirm `rollbackFor` is set when checked exceptions should also trigger a rollback

### 5. Security & Reliability

- [ ] **Authentication/Authorization**: Proper use of `@AuthenticationPrincipal` etc.
- [ ] **Input Validation**: API level `@Valid`, Command `validate()`, domain constructor/record validation
- [ ] **Sensitive Data**: No secret keys or personal information in logs/errors
- [ ] **Web3**: Address validation, signature validation, retry/idempotency handled

### 6. Code Quality

- [ ] **Single Responsibility**: Classes/methods have clear and focused roles
- [ ] **Readability**: No excessive nesting, appropriate method length
- [ ] **Duplication**: Repeated logic is extracted
- [ ] **Error Handling**: Clear error messages, appropriate HTTP status mapping

## Feedback Format

```markdown
### 🔴 Critical (Must fix before merge)
- [FilePath:Line] Specific problem and how to fix

### 🟡 Suggestion (Recommended improvement)
- [FilePath:Line] Suggestion for improvement

### 🟢 Nice to have (Optional)
- [FilePath:Line] Additional enhancement ideas
```

- **Critical**: Architecture violation, security issue, missing tests, malfunction
- **Suggestion**: Convention violation, readability, refactoring
- **Nice to have**: Performance optimization, documentation, minor styling

## Project-specific Notes (MZTK-BE)

### Web3/Ethereum

- Address normalization (lowercase), `0x` prefix validation
- Handle nonce, retry, idempotency
- Follow standards like EIP-4361, EIP-7702

### API Response

- Use the `ApiResponse<T>` wrapper
- Consistent error responses via `GlobalExceptionHandler`
- ErrorCode and HTTP status must match

### Module Boundaries

- Minimize direct dependencies between modules
- Shared logic should go in `global/` or shared modules

## Review Summary Template

```markdown
## Review Summary

- **Overall Evaluation**: ✅ Approve / 🔶 Approve after fixes / ❌ Reject
- **Key Points**:
  - Architecture: ...
  - Testing: ...
  - Security/Performance: ...
- **Critical Issues**: (None / # of issues)
- **Follow-up**: (Optional)
```