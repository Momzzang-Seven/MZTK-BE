# ARCHITECTURE.md — Hexagonal Architecture Guide

This document defines the structural rules for every module under `modules/`. Treat it as a
contract: code that violates the rules below must not pass review.

---

## Package Diagram

```
modules/<name>/
├── api/
│   ├── controller/          ← driving adapter (REST)
│   └── dto/                 ← HTTP request / response shapes
├── application/
│   ├── port/
│   │   ├── in/              ← use-case interfaces (input ports)
│   │   └── out/             ← infrastructure interfaces (output ports)
│   ├── service/             ← use-case implementations
│   └── dto/                 ← commands (input) and results (output)
├── domain/
│   ├── model/               ← aggregate roots, factory/state methods
│   └── vo/                  ← value objects, enums, policy objects
└── infrastructure/
    ├── config/              ← @ConfigurationProperties, @Configuration
    ├── event/               ← @TransactionalEventListener handlers
    ├── persistence/
    │   ├── adapter/         ← implements output ports; owns entity↔domain mapping
    │   ├── entity/          ← JPA entities (no business logic)
    │   └── repository/      ← Spring Data JPA interfaces
    ├── <external>/          ← one sub-package per external system (e.g. s3/)
    └── scheduler/           ← @Scheduled batch drivers
```

Dependency rule: `api` → `application` ← `infrastructure`; `domain` has no outward dependencies.

---

## api Layer

**Responsibility:** Translate HTTP concerns into application commands and map results back to HTTP
responses. No business logic lives here.

**Rules:**
- May only import from `application/port/in/`, `application/dto/`, `domain/model`, `domain/vo/`, and `global/`.
- Must not import `infrastructure` packages.
- Must not import `application/service/` classes directly — always call the use-case interface.

### api/controller — Driving Adapter

**Responsibility:** Receive HTTP requests, convert to application commands, invoke a use-case
interface, and return an `ApiResponse<T>` response.

**Rules:**
- One controller method does exactly three things: (1) build a command from the request DTO,
  (2) call a use-case, (3) wrap the result in `ApiResponse`.
- Inject only use-case interfaces (`*UseCase`), never service classes or repositories.
- Authentication / authorization checks (e.g. null principal guard) belong here, not in the
  service.

```java
// Good
IssuePresignedUrlCommand command = request.toCommand(userId);   // (1)
IssuePresignedUrlResult  result  = useCase.execute(command);    // (2)
return ResponseEntity.ok(ApiResponse.success(
    IssuePresignedUrlResponseDTO.from(result)));                  // (3)
```

### api/dto — HTTP DTOs

**Responsibility:** Represent the JSON body / query params for a single endpoint.

**Rules:**
- Each request DTO owns a `toCommand(...)` method that converts it to an `application/dto/`
  command. The controller must not build commands field-by-field.
- Each response DTO owns a static `from(Result)` factory. The controller must not map fields
  manually.
- No business logic, no persistence references.

---

## application Layer

**Responsibility:** Orchestrate domain objects to fulfil use cases. Defines the boundary that
both driving adapters (api) and driven adapters (infrastructure) depend on.

**Rules:**
- Must not import any class from `api/` or `infrastructure/`.
- Services interact with the outside world exclusively through output port interfaces.

### application/dto — Commands and Results

- **Command** — carries validated input into a use case. May expose a `validate()` method that
  throws `IllegalArgumentException` or a domain exception on bad input.
- **Result** — carries structured output out of a use case toward the caller.
- Commands and Results are plain records or value objects; no Spring annotations.

### application/port/in — Input Ports (Use-Case Interfaces)

- One interface per use case, named `<Verb><Noun>UseCase`.
- Declares a single `execute(Command)` method (or a named equivalent for batch use cases).
- Driving adapters (controllers, schedulers, event handlers) call these interfaces; they never
  reference the service class.

### application/port/out — Output Port Interfaces

- One interface per external capability (persistence, storage, config policy, …).
- Named after what the application needs, not how it is implemented (e.g. `SaveImagePort`, not
  `JpaImageRepository`).
- Value objects shared between a port and its callers (e.g. `PresignedUrlWithKey`) live here.

### application/service — Use-Case Implementations

**Responsibility:** Implement input ports. Orchestrate domain objects and output ports to fulfil
the use case.

**Allowed dependencies:**
- `application/port/out/` interfaces (never their infrastructure implementations)
- `application/dto/` and `application/port/in/`
- `domain/model/` and `domain/vo/`
- `global/` utilities and exceptions
- Other input ports from the **same module** (via interface injection, not direct service
  reference)

**Forbidden:**
- Importing any class from `infrastructure/` or `api/`
- Importing service classes from other modules (use cross-module input port interfaces)

---

## domain Layer

**Responsibility:** Encode invariants, state transitions, and business rules. Must be
framework-free; no Spring, JPA, or external library annotations.

**Rules:**
- No dependency on any other layer — not `application/`, `api/`, `infrastructure/`, or
  `global/` infrastructure beans.
- Domain objects are instantiated through factory methods, not public constructors.
- Using `global/error` is accepted

### domain/model — Aggregate Roots

- Hold state and expose named state-transition methods (e.g. `createPending(...)`,
  `markActive(...)`).
- Factory methods enforce invariants on creation; throw domain exceptions for illegal states.
- No persistence mapping annotations (JPA belongs in `infrastructure/persistence/entity/`).

### domain/vo — Value Objects

- Immutable representations of concepts that have no identity: enums, policy records, tagged
  types.
- May contain pure validation logic (e.g. `AllowedImageExtension.extractExtension(filename)`).
- No mutable state; no external service calls.

---

## infrastructure Layer

**Responsibility:** Implement output ports using real frameworks and external systems (JPA, S3,
config, events, schedulers). Wire adapters into Spring context; keep all Spring/framework
annotations out of `application/` and `domain/`.

**Rules:**
- Implements output port interfaces; the application layer never imports these classes directly.
- May import `domain/` and `application/port/out/` freely; must not import `api/`.

### infrastructure/config

- `@ConfigurationProperties` beans bound from `application.yml` prefixes.
- When a config class directly supplies a policy value the service needs, implement the
  corresponding output port interface on the config class itself.

```java
// ImagePendingCleanupProperties implements LoadPendingImageCleanupPolicyPort
@ConfigurationProperties(prefix = "image.pending-cleanup")
public class ImagePendingCleanupProperties implements LoadPendingImageCleanupPolicyPort {
    private int retentionHours = 5;
    private int batchSize = 100;
}
```

### infrastructure/event — Domain Event Handlers

- Annotated with `@TransactionalEventListener` + `@Transactional(propagation = REQUIRES_NEW)`.
- Listen for domain events published by other modules; call an input port to perform side
  effects.
- Do not import `application/service` directly.
- Do not use `application/port/out` directly.
- event handler is also a driving adapter, so it must rely on `port/in`.
- **Catch and log all exceptions without rethrowing.** A scheduler provides the fallback for
  missed events.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handle(PostDeletedEvent event) {
    try {
        unlinkImagesByReferenceUseCase.execute(command);
    } catch (Exception e) {
        log.error("...", e);  // never rethrow
    }
}
```

### infrastructure/persistence

| Sub-package | Rule |
|-------------|------|
| `adapter/`  | Implements output ports. Owns all entity↔domain mapping (`toEntity` / `toDomain` helpers). Never exposes JPA entities beyond this class. |
| `entity/`   | Pure JPA mapping. No business logic, no domain imports. |
| `repository/` | Spring Data JPA interfaces only. May contain `@Query` / native SQL. No business logic. |

### infrastructure/\<external\>

- One sub-package per external system (e.g. `s3/`, `web3/`).
- Each class implements one or more output ports and is annotated `@Component`.
- Framework SDK objects (e.g. `S3Presigner`) stay inside this package.

### infrastructure/scheduler — Batch Drivers

- Inject a batch input port and loop until the use case returns `0`.
- Cron expression and timezone must be externalised to `application.yml`; never hard-coded.
- import `application/port/in`. scheduler is also a driving adapter, so it must rely on `port/in`.

```java
@Scheduled(cron = "${image.pending-cleanup.cron:0 0 0/1 * * *}",
           zone  = "${image.pending-cleanup.zone:Asia/Seoul}")
public void run() {
    while (true) {
        int n = cleanupUseCase.runBatch(now);
        if (n <= 0) break;
        totalDeleted += n;
    }
}
```

---

## Cross-Module Dependencies

A module's `application/service` must never import another module's `infrastructure` layer directly.
When module **A** needs a capability provided by module **B**, follow this pattern:

1. **Declare the need as an output port** in `A/application/port/out/`
   (`interface BCapabilityPort { Result doSomething(Command cmd); }`)
2. **Implement the port** in `A/infrastructure/external/<b>/` — this adapter is the only place
   that is allowed to import from module B.
3. **The adapter calls B's input port interface** (`B/application/port/in/<Use>CaseUseCase`),
   never B's service class or B's repository.
4. **B's input port is implemented by B's infrastructure** (`B/infrastructure/`) and injected
   by Spring — A never instantiates or references it directly.

```
A/application/service
        │  uses
        ▼
A/application/port/out/BCapabilityPort          ← interface only; no B imports
        │  implemented by
        ▼
A/infrastructure/external/<b>/BCapabilityAdapter  ← only place A imports from B
        │  calls
        ▼
B/application/port/in/SomeUseCaseUseCase        ← interface only
        │  implemented by
        ▼
B/infrastructure/...                            ← B's own adapters
```

**Rules:**
- `A/application/service` must not import any class under `B/` — not service, not entity, not
  port/out.
- `A/infrastructure/external/<b>/` may import only from `B/application/port/in/`,
  `B/application/dto/`, and `B/domain/vo/`. It must not import `B/infrastructure/`.
- If A only needs to read data that B exposes via a use case, reuse B's existing input port
  rather than creating a new one.

### Shared Kernel Exception: `web3/shared`

`modules/web3/shared/` is the **only** shared-kernel module in this codebase. Sibling web3
sub-modules (`transaction/`, `treasury/`, `eip7702/`, `qna/`, `execution/`, `wallet/`,
`challenge/`, `signature/`, `transfer/`) and select non-web3 modules (`level/`) may import
from `web3/shared` at any layer — including `application/service`, `application/port/out/`,
and `application/dto/` — without going through a bridging adapter. This is the established
pattern (see `EvmAddress`, `Vrs`, `KmsKeyState`, `TreasurySigner`,
`ExecutionSignerCapabilityView`).

The exception is justified because:
- `web3/shared` exposes only stable cross-cutting types (VOs, crypto primitives,
  capability-handle DTOs); it has no use cases of its own and never owns business state.
- Routing every `EvmAddress` / `Vrs` use through a bridging adapter would force every web3
  sibling to maintain a parallel re-export, with no architectural benefit.

**Allowed packages from `web3/shared/`:**
- `web3/shared/domain/vo/` — value objects (`EvmAddress`, ...)
- `web3/shared/domain/crypto/` — crypto primitives (`Vrs`, `KmsKeyState`, ...)
- `web3/shared/application/dto/` — capability handles carrying no secret material
  (`TreasurySigner`, `ExecutionSignerCapabilityView`, ...)
- `web3/shared/application/port/in/` — input ports, when calling a `web3/shared` use case
- `web3/shared/application/util/` — stateless cross-cutting helpers with no business state
  (`Erc20TransferCalldataEncoder`, ...)

**Still forbidden from `web3/shared/`:**
- `web3/shared/infrastructure/` — never import directly; use the standard port/adapter
  pattern. The single `infrastructure/config/` exception (`@ConditionalOnUserExecutionEnabled`)
  is a Spring annotation, not a class dependency.
- `web3/shared/application/port/out/` — these are `web3/shared`'s own out-ports; siblings
  must declare their own ports if they need the same capability.

Adding a new shared kernel beyond `web3/shared` requires updating this section.

---

## Dependency Summary

```
api  ──────────────────────────────────────►  application/port/in
                                                      │
                                               application/service
                                                      │
infrastructure  ───── implements ──────►  application/port/out
                                                      │
                                                   domain
```

- `api` → `application` (port/in only)
- `infrastructure` → `application` (port/out only) + `domain`
- `application/service` → `domain` + `application/port/out` + `global`
- `domain` → nothing

Cross-layer imports not listed above are **forbidden**.
