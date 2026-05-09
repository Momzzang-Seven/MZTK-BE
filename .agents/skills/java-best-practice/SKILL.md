---
name: java-best-practice
description: Java coding conventions and MZTK-BE project style guide. Apply when writing Java code, refactoring, or designing domain models. Use when writing Java code, refactoring, or defining domain models.
---

# Java Best Practice (MZTK-BE)

## Purpose

Defines the Java coding style and conventions for the MZTK-BE project. Ensures the AI agent generates consistent code.

## Package and Module Structure

- Base package: `momzzangseven.mztkbe`
- Per-module: `modules/{module_name}/` (e.g. `web3/challenge`, `user`, `auth`). All web3-related modules must exist under the `web3/` namespace.
- Global shared layer: `global/` — infrastructure/components shared throughout the application
- hexagonal architecture: @MZTK-BE/docs.shared/ARCHITECTURE.md

### Folders under global

| Folder      | Role                                      | Example                                          |
|-------------|-------------------------------------------|--------------------------------------------------|
| `error/`    | Centralized exception & error handling    | `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, domain-specific exceptions (`web3/`, `wallet/`, `challenge/`, `auth/` etc.) |
| `response/` | Standardize API response format           | `ApiResponse<T>`                                 |
| `config/`   | Global Spring configuration               | `QueryDslConfig`, `FlywayConfiguration`, `OpenApiConfig`  |
| `security/` | Authentication, authorization, encryption | `SecurityConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `AesGcmCipher` |
| `time/`     | Time & timezone config                    | `TimeConfig` (Clock, ZoneId Bean)                |
| `audit/`    | Audit log port                            | `RecordAdminAuditPort`                           |

### global Folder Examples

**error/** — Use `ErrorCode` enum for management of code/message/httpStatus, and derive exceptions from `BusinessException`:

```java
// ErrorCode: AUTH_001, WALLET_001, WEB3_001 etc. by domain
public enum ErrorCode {
  USER_NOT_FOUND("AUTH_001", "User not found", HttpStatus.UNAUTHORIZED),
  WEB3_INVALID_INPUT("WEB3_001", "Invalid web3 input", HttpStatus.BAD_REQUEST),
  // ...
}

// Domain exceptions derive from BusinessException
public class Web3InvalidInputException extends BusinessException {
  public Web3InvalidInputException(String message) {
    super(ErrorCode.WEB3_INVALID_INPUT, message);
  }
}
```

**response/** — Use `ApiResponse.success(data)`, `ApiResponse.error(message, code)`.

**config/** — QueryDsl `JPAQueryFactory`, Flyway migration, OpenAPI/Swagger config.

**security/** — JWT auth, CORS, 401/403 handler, encryption (`AesGcmCipher`), etc.

## Domain Model

### Immutable Entities

```java
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferPrepare {

  private final String prepareId;
  private final Long fromUserId;
  // ... only use final fields

  /** Provide domain logic as methods */
  public boolean isActiveAt(LocalDateTime now) {
    return authExpiresAt != null && authExpiresAt.isAfter(now);
  }

  /** Always return a new instance on state change (immutability) */
  public TransferPrepare expire() {
    if (status == TransferPrepareStatus.EXPIRED) {
      throw new Web3InvalidInputException("transfer prepare is already expired");
    }
    return toBuilder().status(TransferPrepareStatus.EXPIRED).build();
  }
}
```

- Use `@Builder(toBuilder = true)` for new instance creation on state change
- Constructors should be private; prefer static factory methods
- Throw explicit exceptions on domain rule violation (e.g. `Web3InvalidInputException`)

### Value Object (VO)

```java
public record ChallengeConfig(
    int ttlSeconds, String domain, String uri, String version, String chainId) {

  /** Validate in compact constructor */
  public ChallengeConfig {
    if (ttlSeconds <= 0) throw new IllegalArgumentException("TTL must be positive");
    if (domain == null || domain.isBlank()) throw new IllegalArgumentException("Domain must not be blank");
    // ...
  }
}
```

- Use `record`, validate in the compact constructor

## DTOs and Commands

### Request DTO (API layer)

```java
public record CreateChallengeRequestDTO(
    @NotNull(message = "Purpose must not be null") ChallengePurpose purpose,
    @NotNull(message = "Wallet address must not be null") String walletAddress) {}
```

- Use `record` + Bean Validation (`@NotNull`, `@NotBlank`, etc)

### Command / Result (Application layer)

```java
// Command: UseCase input
public record CreateChallengeCommand(Long userId, ChallengePurpose purpose, String walletAddress) {
  public void validate() {
    if (userId == null || userId <= 0) throw new IllegalArgumentException("User ID must be positive");
    if (purpose == null) throw new IllegalArgumentException("Purpose must not be null");
    if (walletAddress == null || walletAddress.isBlank()) throw new IllegalArgumentException("Wallet address must not be blank");
    // Additional format checks etc.
  }
}

// Result: UseCase output
public record CreateChallengeResult(String nonce, String message, int expiresIn) {
  public static CreateChallengeResult from(Challenge saved, int ttlSeconds) {
    return new CreateChallengeResult(saved.getNonce(), saved.getMessage(), ttlSeconds);
  }
}
```

- Commands must validate business rules with a `validate()` method
- Results should use a static factory (`from`)

## Lombok Usage

| Annotation                | Purpose                                |
|---------------------------|----------------------------------------|
| `@Getter`                 | Domain model (forbid setter)           |
| `@Builder(toBuilder = true)` | Immutable object build/copy         |
| `@AllArgsConstructor(access = PRIVATE)` | Constructor for builder |
| `@RequiredArgsConstructor` | Constructor injection for DI (final fields) |
| `@Slf4j`                  | Logging                               |

- Do not use `@Setter` on domain models

## Exception Handling

- Business exceptions: Derive from `BusinessException`, use `ErrorCode`
- Domain-specific exceptions: e.g. `Web3InvalidInputException`
- Global exception handler (`GlobalExceptionHandler`) converts to `ApiResponse`
- Client input errors: `IllegalArgumentException` → HTTP 400

## API Response

- Standard wrapper: `ApiResponse<T>`
- Success: `ApiResponse.success(data)` or `ApiResponse.success(message, data)`
- Failure: `ApiResponse.error(message, code)` (use ErrorCode.code)

## Controller

```java
@Slf4j
@RestController
@RequestMapping("/web3/challenges")
@RequiredArgsConstructor
public class ChallengeController {

  private final CreateChallengeUseCase createChallengeUseCase;

  @PostMapping
  public ResponseEntity<ApiResponse<ChallengeResponseDTO>> createChallenge(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateChallengeRequestDTO request) {
    userId = requireUserId(userId);
    CreateChallengeCommand command = new CreateChallengeCommand(userId, request.purpose(), request.walletAddress());
    CreateChallengeResult result = createChallengeUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(ChallengeResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) throw new UserNotAuthenticatedException();
    return userId;
  }
}
```

- Directly inject UseCase, perform only DTO ↔ Command conversion
- Use `@Valid` for request DTO validation
- Use `@AuthenticationPrincipal` for authenticated user
- Use folloing code for validating the userId is not null
```java
  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
```


## Naming

- UseCase interface: `{Verb}{Target}UseCase` (e.g. `CreateChallengeUseCase`)
- Port: `{Verb}{Target}Port` (e.g. `SaveChallengePort`, `LoadChallengeConfigPort`)
- Service: `{Verb}{Target}Service` + `implements {UseCase}`
- Adapter: `{Target}{Role}Adapter` (e.g. `TransferPreparePersistenceAdapter`)

## Comments

- Write detailed descriptions of the roles of classes and methods using Javadoc-style comments in English.