---
name: generate-test-code
description: |
  Generates Java test code for the MZTK-BE Spring Boot project based on a test case document.
  Use this skill whenever the user asks to "테스트 코드 작성", "테스트 구현", "generate test code",
  "implement test cases", or any similar request that involves turning documented test cases into
  runnable Java (or TypeScript) test files. This skill should also trigger when the user says
  things like "test case 문서 기반으로 테스트 짜줘", "테스트 케이스 구현해줘", or "write tests from the spec".
  The skill covers all three test tiers: Module Tests (unit + H2 integration), E2E Tests
  (local server + local DB), and Playwright Tests (external API).
---

# Generate Test Code — MZTK-BE

This skill translates a structured test case document into runnable Java (and TypeScript) test code
following the MZTK-BE project conventions.

---

## Step 1: Locate the test case document

1. Get the current branch name: `git rev-parse --abbrev-ref HEAD`
2. Read the test case document at:
   ```
   docs.local/test/<branch-name>/<branch-name>.md
   ```
3. Parse the document sections — each top-level heading maps to a test tier:
   - `## 1. Module Test` (or "Module Test") → unit/H2 integration tests
   - `## 2. E2E Test` (or "E2E Test") → local server + local DB integration tests
   - `## 3. Playwright Test` (or "Playwright Test") → TypeScript E2E with real external systems

Every test case `[M-N]`, `[E-N]`, `[P-N]` in the document must be implemented — no cases may be skipped.

---

## Step 2: Determine the module and target paths

Look at the branch name and the test case targets to identify the module name (e.g., `image`, `location`, `auth`).

### Module Test → `src/test/java/momzzangseven/mztkbe/modules/<module>/`

Place each test class in the layer that matches its target:

| Target in test case | Layer path |
|---|---|
| Command/DTO `validate()` | `application/dto/` |
| Service `execute()` | `application/service/` |
| Controller endpoint | `api/` |
| Persistence adapter | `infrastructure/persistence/adapter/` |
| External API adapter | `infrastructure/external/` |

If the module folder already exists, add to the existing files. If not, create the module folder and subfolders.

### E2E Test → `src/test/java/momzzangseven/mztkbe/integration/e2e/<module>/`

Create `<FeatureName>E2ETest.java` (e.g., `GetImagesByIdsE2ETest.java`). Add to existing module folder if it exists.

### Playwright Test → `src/test/java/momzzangseven/mztkbe/integration/play_wright/<feature-name>/`

Create `<feature-name>.spec.ts` and a stub `<feature-name>-report.md`.

---

## Step 3: Write the test code

### Module Tests — annotations and structure

**DTO/Command unit test** (no Spring, no DB):
```java
@DisplayName("<ClassName> 단위 테스트")
class <ClassName>Test {
    @Nested @DisplayName("성공 케이스") class SuccessCases { ... }
    @Nested @DisplayName("<Field> 검증") class <Field>Validation { ... }
}
```

**Service unit test** (Mockito only, no Spring):
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("<ServiceName> 단위 테스트")
class <ServiceName>Test {
    @Mock private <OutputPort> port;
    @InjectMocks private <ServiceName> service;

    @Nested @DisplayName("성공 케이스") class SuccessCases { ... }
    @Nested @DisplayName("실패 케이스") class FailureCases { ... }
    @Nested @DisplayName("엣지 케이스") class EdgeCases { ... }
}
```

**Controller integration test** (full Spring + H2 + MockMVC):
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional  // rolls back after each test — no @AfterEach DB cleanup needed here
@DisplayName("<Feature> 전체 통합 테스트 (MockMVC + H2)")
class <Feature>ControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private <UseCase> useCase;  // mock the use case, not the repo
    ...
}
```

Key annotations cheatsheet:
- `@Mock` / `@InjectMocks` — Mockito unit tests
- `@MockitoBean` — replace a Spring bean in the context
- `@WithMockUser` — inject a mock authenticated user
- `@ParameterizedTest` + `@ValueSource` / `@NullSource` — multi-value tests
- `@Nested` + `@DisplayName` — logical grouping (Korean names are preferred)

BDD style in every test:
```java
// given
given(port.method(arg)).willReturn(value);
// when
Result result = service.execute(command);
// then
assertThat(result.field()).isEqualTo(expected);
verify(port, times(1)).method(arg);
```

### E2E Tests — annotations and DB cleanup

```java
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] <Feature> 전체 흐름 테스트")
class <Feature>E2ETest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private <Repository> repository;  // for setup/teardown

    private Long createdId;

    @AfterEach
    void tearDown() {
        // Remove any data inserted during setup
        if (createdId != null) {
            repository.deleteById(createdId);
            createdId = null;
        }
    }
    ...
}
```

**DB cleanup rule:** If a test inserts or modifies rows, the `@AfterEach` method must restore the DB to its pre-test state. Use repository `delete` calls or `@Sql` cleanup scripts. For read-only tests (no writes), no cleanup is necessary.

**Authentication in E2E:** Obtain a JWT by hitting the login endpoint, or use a pre-seeded test user token. Pass it as `Authorization: Bearer <token>`.

### Playwright Tests — TypeScript structure

```typescript
// <feature-name>.spec.ts
import { test, expect, request } from '@playwright/test';

test.describe('<Feature> E2E', () => {
  let accessToken: string;

  test.beforeAll(async () => {
    const api = await request.newContext({ baseURL: process.env.BACKEND_URL });
    // login to get accessToken
  });

  test('[P-N] <scenario description>', async () => {
    const api = await request.newContext({
      baseURL: process.env.BACKEND_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` },
    });
    // ... steps ...
    expect(res.status()).toBe(200);
  });
});
```

---

## Step 4: Quality checklist before finishing

- Every `[M-N]`, `[E-N]`, `[P-N]` case from the document has a corresponding `@Test` method.
- Test method names follow: `<method>_<condition>_<expectedResult>()` (camelCase, English).
- `@DisplayName` values include the case ID, e.g. `"[M-1] Valid command passes without exception"`.
- Parameterized cases use `@ParameterizedTest` with `@ValueSource` or `@CsvSource`, not copy-pasted methods.
- E2E tests that write to DB have an `@AfterEach` teardown.
- Controller tests use `@Transactional` (rollback) — no manual teardown needed.
- All imports resolve to classes that actually exist in the production code (read the source if unsure).
- Code follows Google Java Format (the project enforces `spotlessCheck`).

---

## Image endpoint gotchas

`GET /images` enforces 3-factor ownership: `userId`, `referenceType`, **and `referenceId`**.
Images created via `POST /images/presigned-urls` have `referenceId = null` until they are linked
to a parent resource. Querying them before linking returns HTTP 403 (`ImageNotBelongsToUserException`).

**Always link the image before calling `GET /images`:**
1. `POST /images/presigned-urls` → `imageId`
2. Upload to S3
3. `POST /posts/free` (or the appropriate parent resource endpoint) → `postId`
4. `PATCH /posts/{postId}` with `imageIds: [imageId]` — sets `image.referenceId = postId`
5. `GET /images?ids={imageId}&referenceType=COMMUNITY_FREE&referenceId={postId}` ✅

This applies to both E2E and Playwright tests. Remember to delete the created post in `@AfterEach` / `afterAll`.

---

## Notes on project conventions

- Exception types live in `global/error/<domain>/` (e.g. `global/error/image/`, `global/error/post/`) — check existing code before inventing class names. A few AI-specific exceptions in `modules/verification/application/exception/` are the only module-level exceptions.
- `ApiResponse` wrapper: assert `$.status == "SUCCESS"` or `$.status == "ERROR"` and `$.data.*` fields.
- Auth token injection in MockMVC: use `SecurityMockMvcRequestPostProcessors.authentication(...)` or `@WithMockUser`.
- The project uses AssertJ (`assertThat`, `assertThatThrownBy`, `assertThatNoException`) — avoid JUnit 4 `assertEquals`.
- `verify(port, never()).method(any())` — confirm no spurious port calls on early-exit paths.