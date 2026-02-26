# 테스트 작성 가이드 문서
이 문서는 팀원 및 AI 에이전트가 테스트코드를 작성할 때 가이드로서 작용하는 문서입니다.

# 테스트 패키지 구조
- test
    - java
        - momzzangseven.mztkbe
            `README.md`
            - integration
                - playWright (E2E Test, 외부 api 응답까지 확인)
                    - **📌 NOTE! playwrite 디렉터리는 외부 API까지 연동된 통합테스트가 필요한 경우에만 작성합니다.**
                    - **📌 외부 API 응답까지 포함한 E2E 테스트 결과물을 .md파일로 작성해야 합니다.**
                    - **📌 여기에는 .java코드가 들어가지 않습니다. (ts 기반 스크립트)**
                    1. `playwrite를 위한 스크립트 파일`
                    2. `playwrite 수행 후 결과 보고서 (.md파일 형식)` 
                - e2e (Local server + Local DB)
                    - **📌 NOTE! e2e 디렉터리에는 .java 코드가 들어갑니다.**
                    - **📌 로컬 서버와 로컬 DB서버 이용한 "실제 DB 상호작용 테스트" 가 목적입니다.**
                    - `테스트 코드`
            - modules
                - {모듈 이름}
                    - api (MockMVC + H2 DB)
                        - **📌 NOTE! integration 디렉터리에는 .java코드가 들어갑니다.**
                        - **📌 H2 기반의 테스트를 돌림으로써 코드의 문제 검증이 목적입니다.**
                        - `테스트 코드`
                    - application (Unit Test)
                        - `테스트코드`
                    - domain (Unit Test)
                        - `테스트코드`
                    - infrastructure (Unit Test)
                        - `테스트코드`

---

# 패키지 다이어그램

```
momzzangseven.mztkbe (test root)
│
├── README.md                          ← 이 문서
│
├── integration/                       ← 통합 테스트 (레이어 간 실제 연동 검증)
│   ├── e2e/                           ← 로컬 서버 + 로컬 DB 기반 E2E (Java)
│   │   └── {기능명}E2ETest.java
│   │
│   └── play_write/                    ← 외부 API 연동 E2E (ts 스크립트 + MD 보고서)
│       ├── {기능명}.spec.ts
│       └── {기능명}-report.md
│
└── modules/                           ← 단위 테스트 (모듈별 격리 검증)
    └── {모듈명}/                       예) location, web3, auth, level ...
        ├── api/                       ← Controller 테스트 (MockMvc + H2)
        │   └── {기능명}ControllerTest.java
        ├── application/
        │   ├── service/               ← Service 단위 테스트 (Mockito)
        │   │   └── {기능명}ServiceTest.java
        │   └── dto/                   ← DTO 변환/검증 단위 테스트
        │       └── {Dto명}Test.java
        ├── domain/
        │   ├── model/                 ← 도메인 모델 비즈니스 로직 단위 테스트
        │   │   └── {모델명}Test.java
        │   └── vo/                    ← Value Object 단위 테스트
        │       └── {VO명}Test.java
        └── infrastructure/
            ├── persistence/
            │   ├── adapter/           ← Persistence Adapter 단위 테스트 (Mockito)
            │   │   └── {어댑터명}Test.java
            │   └── entity/            ← JPA Entity 매핑 단위 테스트
            │       └── {엔티티명}Test.java
            └── external/              ← 외부 API 어댑터 단위 테스트 (Mockito)
                └── {어댑터명}Test.java
```

---

# 각 패키지의 목적

## `integration/e2e/`

**목적**: 로컬에서 실제 Spring Boot 서버와 로컬 PostgreSQL DB를 함께 기동하여, HTTP 요청부터 DB 저장까지의 전체 흐름을 검증합니다.

- 실제 DB 스키마와 Flyway 마이그레이션이 올바르게 적용되는지 확인
- Controller → Service → Repository → DB의 레이어 간 연결 이상 여부 검증
- **외부 API(카카오, 구글 등)는 `@MockBean` 처리**하여 외부 의존성 제거
- `@Tag("e2e")` 로 CI 파이프라인에서 선택적 실행 가능

## `integration/play_write/`

**목적**: 실제 외부 API까지 포함한 완전한 E2E 시나리오를 Playwright(TypeScript)로 검증합니다.

- 카카오 Geocoding API, Google OAuth 등 외부 서비스까지 실제 연동
- `.spec.ts` 스크립트로 시나리오를 작성하고, 실행 결과를 `.md` 보고서로 저장
- **`.java` 파일 없음** — TS 기반 스크립트만 존재

## `modules/{모듈명}/api/`

**목적**: Controller 레이어를 MockMvc + H2 인메모리 DB로 테스트합니다.

- HTTP 요청/응답 형식(상태코드, JSON 구조) 검증
- Spring Security 필터 체인 통과 여부 확인
- Service 레이어는 `@MockBean`으로 대체

## `modules/{모듈명}/application/service/`

**목적**: Service의 비즈니스 로직을 Port를 Mockito로 Mock하여 순수하게 단위 테스트합니다.

- 각 Port(`LoadXxxPort`, `SaveXxxPort` 등)의 호출 여부 및 횟수 검증
- 성공/실패/예외 분기 케이스 망라
- Spring 컨텍스트 없이 `@ExtendWith(MockitoExtension.class)` 만으로 실행

## `modules/{모듈명}/application/dto/`

**목적**: DTO의 팩토리 메서드, 변환 로직, 유효성 검사를 단위 테스트합니다.

- `from()`, `of()` 같은 정적 팩토리 메서드의 변환 정확성 검증
- null/빈값 입력 시 예외 발생 여부 확인

## `modules/{모듈명}/domain/model/` 및 `domain/vo/`

**목적**: 도메인 모델과 Value Object의 비즈니스 규칙을 외부 의존성 없이 순수 단위 테스트합니다.

- 상태 전이(예: `ACTIVE → UNLINKED`) 검증
- 불변식(invariant) 및 입력 유효성 검증
- `equals`/`hashCode` 계약 준수 확인

## `modules/{모듈명}/infrastructure/persistence/`

**목적**: Persistence Adapter의 도메인↔엔티티 변환 및 Repository 위임 로직을 Mockito로 단위 테스트합니다.

- `JpaRepository` Mock을 주입하여 어댑터 내 변환 로직만 검증
- 실제 DB 연결 없이 빠르게 실행

## `modules/{모듈명}/infrastructure/external/`

**목적**: 외부 API 클라이언트 어댑터(카카오, Google 등)를 HTTP 클라이언트 Mock으로 단위 테스트합니다.

- 정상 응답, null 응답, 빈 응답, API 에러(4xx/5xx)에 대한 예외 처리 검증
- 실제 네트워크 호출 없이 `@Mock` 처리

---

# `integration/e2e/` — 테스트 작성 가이드

## 사용 가능한 어노테이션

| 어노테이션 | 설명 |
|---|---|
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` | 실제 서버를 랜덤 포트로 기동 |
| `@AutoConfigureMockMvc` | MockMvc 자동 설정 (포트 없이 슬라이스 테스트 시) |
| `@Transactional` | 테스트 후 DB 롤백 (오염 방지) |
| `@Sql(scripts = "...")` | 테스트 전 SQL 스크립트로 픽스처 데이터 삽입 |
| `@Tag("e2e")` | `./gradlew test -Dgroups=e2e` 로 선택 실행 |
| `@TestPropertySource` | 테스트 전용 프로퍼티 오버라이드 |
| `@MockBean` | 외부 API 어댑터 등 특정 Bean만 Mock 처리 |
| `@WithMockUser` | Spring Security 인증 컨텍스트 주입 |

## 코드 예시 — 위치 등록 E2E 테스트

```java
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@DisplayName("[E2E] 위치 등록 전체 흐름 테스트")
class RegisterLocationE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private GeocodingPort geocodingPort; // 외부 API만 Mock 처리

    @BeforeEach
    void setUp() {
        given(geocodingPort.geocode(anyString()))
            .willReturn(CoordinatesInfo.of(37.5665, 126.9780));
        given(geocodingPort.reverseGeocode(anyDouble(), anyDouble()))
            .willReturn(AddressInfo.of("서울특별시 중구 세종대로 110", "04524"));
    }

    @Test
    @Sql("/sql/fixtures/user.sql") // 테스트용 유저 데이터 삽입
    @DisplayName("유효한 주소로 위치 등록 시 DB에 저장되고 응답 반환")
    void registerLocation_validRequest_savedToDB() {
        // given
        String accessToken = obtainAccessToken();
        RegisterLocationRequest request =
            new RegisterLocationRequest("서울시청", "서울특별시 중구 세종대로 110", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // when
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
            "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            ApiResponse.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().isSuccess()).isTrue();
    }
}
```

## 주의사항

- **외부 API**(카카오, Google OAuth 등)는 반드시 `@MockBean`으로 대체합니다.
- 테스트 DB는 로컬 PostgreSQL를 사용하며, `application-test.yml`에 DataSource를 별도 설정합니다.
- `@Transactional`을 붙이면 테스트 종료 후 자동 롤백되어 DB 오염을 방지합니다.
- 픽스처 데이터가 필요한 경우 `@Sql`을 사용하고, `src/test/resources/sql/fixtures/`에 SQL 파일을 위치시킵니다.

---

# `modules/{모듈}/` — 단위 테스트 작성 가이드

## 사용 가능한 어노테이션

### Service / Infrastructure Adapter 테스트 (Mockito 기반)

| 어노테이션 | 설명 |
|---|---|
| `@ExtendWith(MockitoExtension.class)` | Spring 컨텍스트 없이 Mockito만 사용 (필수) |
| `@Mock` | 인터페이스/클래스를 Mock 객체로 생성 |
| `@InjectMocks` | `@Mock` 객체들을 주입받는 테스트 대상 클래스 생성 |
| `@Spy` | 실제 객체를 감싸서 일부 메서드만 Mock |
| `@Captor` | `ArgumentCaptor` 자동 생성 (호출 인자 검증) |

### Controller 테스트 (MockMvc + H2)

| 어노테이션 | 설명 |
|---|---|
| `@WebMvcTest(XxxController.class)` | Controller 레이어만 로드, Service는 `@MockBean` 처리 |
| `@MockBean` | Spring 컨텍스트의 Bean을 Mock으로 대체 |
| `@WithMockUser(roles = "USER")` | 인증된 사용자 컨텍스트 주입 |
| `@AutoConfigureMockMvc` | MockMvc 자동 설정 |

### 공통 테스트 구조 어노테이션

| 어노테이션 | 설명 |
|---|---|
| `@DisplayName("...")` | 테스트/그룹 이름 지정 (한글 권장) |
| `@Nested` | 논리적 테스트 그룹 분리 (성공/실패/엣지케이스) |
| `@Test` | 단일 테스트 메서드 |
| `@BeforeEach` / `@AfterEach` | 각 테스트 전/후 실행 |
| `@ParameterizedTest` | 여러 입력값으로 동일 로직 반복 테스트 |
| `@ValueSource` / `@CsvSource` | 파라미터화 테스트 입력값 소스 |
| `@NullSource` / `@EmptySource` | null 및 빈값 파라미터화 테스트 |

## 코드 예시 — Service 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("GetMyLocationsService 단위 테스트")
class GetMyLocationsServiceTest {

    @Mock
    private LoadLocationPort loadLocationPort;

    @InjectMocks
    private GetMyLocationsService getMyLocationsService;

    @Nested
    @DisplayName("execute() - 성공 케이스")
    class SuccessCases {

        @Test
        @DisplayName("userId에 해당하는 위치 목록을 반환한다")
        void execute_returnsLocations() {
            // given
            Long userId = 1L;
            given(loadLocationPort.findByUserId(userId))
                .willReturn(List.of(buildLocation(1L, userId, "헬스장"),
                                    buildLocation(2L, userId, "도서관")));

            // when
            GetMyLocationsResult result = getMyLocationsService.execute(userId);

            // then
            assertThat(result.totalCount()).isEqualTo(2);
            assertThat(result.locations()).hasSize(2);
            verify(loadLocationPort, times(1)).findByUserId(userId);
        }

        @Test
        @DisplayName("등록된 위치가 없으면 빈 결과를 반환한다")
        void execute_returnsEmptyResult_whenNoLocations() {
            // given
            Long userId = 99L;
            given(loadLocationPort.findByUserId(userId)).willReturn(List.of());

            // when
            GetMyLocationsResult result = getMyLocationsService.execute(userId);

            // then
            assertThat(result.totalCount()).isZero();
            assertThat(result.locations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("execute() - 실패 케이스")
    class FailureCases {

        @Test
        @DisplayName("userId가 null이면 예외를 던진다")
        void execute_throwsException_whenUserIdIsNull() {
            assertThatThrownBy(() -> getMyLocationsService.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
```

## 코드 예시 — Infrastructure Adapter 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("LocationPersistenceAdapter 단위 테스트")
class LocationPersistenceAdapterTest {

    @Mock
    private LocationJpaRepository locationJpaRepository;

    @InjectMocks
    private LocationPersistenceAdapter locationPersistenceAdapter;

    @Test
    @DisplayName("save() - 도메인 모델을 엔티티로 변환 후 저장하고 결과를 반환한다")
    void save_convertsAndPersists() {
        // given
        Location location = buildLocation(null, 1L, "서울시청");
        LocationEntity savedEntity = buildEntity(100L, 1L, "서울시청");
        given(locationJpaRepository.save(any(LocationEntity.class))).willReturn(savedEntity);

        // when
        Location result = locationPersistenceAdapter.save(location);

        // then
        assertThat(result.getId()).isEqualTo(100L);
        verify(locationJpaRepository, times(1)).save(any(LocationEntity.class));
    }
}
```

## 코드 예시 — Controller 단위 테스트 (MockMvc)

```java
@WebMvcTest(LocationController.class)
@DisplayName("LocationController 단위 테스트")
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetMyLocationsUseCase getMyLocationsUseCase;

    @Test
    @WithMockUser
    @DisplayName("GET /users/me/locations - 위치 목록 조회 성공 시 200 반환")
    void getMyLocations_returns200() throws Exception {
        // given
        GetMyLocationsResult result = GetMyLocationsResult.from(List.of());
        given(getMyLocationsUseCase.execute(any())).willReturn(result);

        // when & then
        mockMvc.perform(get("/users/me/locations")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    @DisplayName("GET /users/me/locations - 인증 없이 요청 시 401 반환")
    void getMyLocations_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/users/me/locations"))
            .andExpect(status().isUnauthorized());
    }
}
```

## 테스트 구조 권장 패턴

### Nested 클래스로 케이스 분류

```java
@Nested @DisplayName("성공 케이스") class SuccessCases { ... }
@Nested @DisplayName("실패 케이스") class FailureCases { ... }
@Nested @DisplayName("엣지 케이스") class EdgeCases { ... }
```

### ArgumentCaptor — 호출된 인자 상세 검증

```java
// 저장된 객체의 필드값까지 검증해야 할 때 사용
ArgumentCaptor<UserWallet> walletCaptor = ArgumentCaptor.forClass(UserWallet.class);
verify(saveWalletPort).save(walletCaptor.capture());
assertThat(walletCaptor.getValue().getUserId()).isEqualTo(expectedUserId);
assertThat(walletCaptor.getValue().getStatus()).isEqualTo(WalletStatus.ACTIVE);
```

### ParameterizedTest — 다양한 입력값 반복 검증

```java
@ParameterizedTest
@NullSource
@ValueSource(strings = {"", "   "})
@DisplayName("주소가 null 또는 공백이면 예외 발생")
void geocode_throwsException_whenAddressIsBlank(String address) {
    assertThatThrownBy(() -> geocodingAdapter.geocode(address))
        .isInstanceOf(GeocodingFailedException.class);
}
```

---

# Playwright E2E 테스트 설정 가이드

## 개요

`integration/play_write/` 디렉터리에는 TypeScript 기반 Playwright 스크립트를 작성합니다.  
**.java 파일이 존재하지 않으며**, 실행 결과는 반드시 `.md` 보고서로 저장합니다.

## 1. 사전 요구사항

```bash
# Node.js 18 이상 필요
node --version

# play_write 디렉터리로 이동 후 패키지 초기화
cd src/test/java/momzzangseven/mztkbe/integration/play_write
npm init -y
npm install -D @playwright/test

# 브라우저 바이너리 설치 (chromium만 설치해도 충분)
npx playwright install chromium
```

## 2. 디렉터리 구조

```
integration/play_write/
├── playwright.config.ts         ← Playwright 전역 설정
├── package.json
├── .env                         ← 테스트용 민감 정보 (gitignore 필수!)
├── {기능명}.spec.ts              ← 테스트 시나리오 스크립트
└── {기능명}-report.md            ← 실행 결과 보고서
```

## 3. `playwright.config.ts` 기본 설정

```typescript
import { defineConfig } from '@playwright/test';
import dotenv from 'dotenv';

dotenv.config();

export default defineConfig({
  testDir: '.',
  timeout: 30_000,
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
    },
  },
  reporter: [
    ['list'],
    ['json', { outputFile: 'test-results.json' }],
  ],
});
```

## 4. API 테스트 스크립트 예시 — 지갑 등록 E2E

```typescript
// wallet-registration.spec.ts
import { test, expect, request } from '@playwright/test';

test.describe('지갑 등록 E2E', () => {
  let accessToken: string;
  let challengeNonce: string;

  test.beforeAll(async () => {
    // Step 0: 로그인하여 액세스 토큰 발급
    const api = await request.newContext({ baseURL: process.env.BASE_URL });
    const loginRes = await api.post('/auth/login', {
      data: { provider: 'KAKAO', code: process.env.KAKAO_TEST_CODE },
    });
    expect(loginRes.ok()).toBeTruthy();
    const loginBody = await loginRes.json();
    accessToken = loginBody.data.accessToken;
  });

  test('챌린지 발급 → 서명 → 지갑 등록 전체 흐름', async () => {
    const api = await request.newContext({
      baseURL: process.env.BASE_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` },
    });

    // Step 1: 챌린지 발급
    const challengeRes = await api.post('/web3/challenges', {
      data: {
        walletAddress: process.env.TEST_WALLET_ADDRESS,
        purpose: 'WALLET_REGISTRATION',
      },
    });
    expect(challengeRes.status()).toBe(201);
    const challengeBody = await challengeRes.json();
    challengeNonce = challengeBody.data.nonce;
    const message = challengeBody.data.message;

    // Step 2: 서명 생성 (테스트 전용 개인키 사용)
    const signature = await signMessage(message, process.env.TEST_PRIVATE_KEY!);

    // Step 3: 지갑 등록
    const registerRes = await api.post('/web3/wallets', {
      data: {
        walletAddress: process.env.TEST_WALLET_ADDRESS,
        signature,
        nonce: challengeNonce,
      },
    });
    expect(registerRes.status()).toBe(201);

    const registerBody = await registerRes.json();
    expect(registerBody.data.walletAddress).toBe(
      process.env.TEST_WALLET_ADDRESS!.toLowerCase()
    );
  });
});
```

## 5. `.env` 파일 설정

`play_write/` 디렉터리 안에 `.env` 파일을 생성합니다. 이 파일은 **반드시 `.gitignore`에 포함**시켜야 합니다.

```bash
# play_write/.env
BASE_URL=http://localhost:8080
TEST_WALLET_ADDRESS=0x...
TEST_PRIVATE_KEY=0x...       # 반드시 테스트 전용 지갑 키만 사용
KAKAO_TEST_CODE=...          # 카카오 OAuth 테스트용 인가 코드
```

> ⚠️ **주의**: `TEST_PRIVATE_KEY`는 **실제 자산이 있는 지갑의 키를 절대 사용하지 마세요.**  
> 테스트 전용 빈 지갑을 새로 생성하여 사용하세요.

## 6. 실행 방법

```bash
# play_write 디렉터리로 이동
cd src/test/java/momzzangseven/mztkbe/integration/play_write

# 로컬 Spring Boot 서버를 먼저 기동한 뒤 실행
npx playwright test

# 특정 스크립트만 실행
npx playwright test wallet-registration.spec.ts

# 결과를 HTML 리포트로 시각화
npx playwright show-report
```

## 7. 결과 보고서 작성 규칙

Playwright 실행 후, 아래 양식에 따라 `{기능명}-report.md` 파일을 `play_write/` 디렉터리에 저장합니다.

```markdown
# {기능명} E2E 테스트 결과 보고서

- **실행일시**: 2025-XX-XX HH:MM
- **테스터**: 홍길동
- **대상 서버**: http://localhost:8080 (dev)
- **외부 API 연동 여부**: 카카오 Geocoding API (실제 연동)

## 테스트 시나리오

| # | 시나리오 | 결과 | 비고 |
|---|---|---|---|
| 1 | 챌린지 발급 | ✅ PASS | 응답시간 120ms |
| 2 | 서명 생성 및 전달 | ✅ PASS | |
| 3 | 지갑 등록 | ✅ PASS | DB 저장 확인 |

## 실패 항목 (있을 경우)

없음

## 비고

(특이사항 기재)
```

---

# 테스트 실행 명령어 요약

```bash
# 전체 단위 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests "*.GetMyLocationsServiceTest"

# e2e 태그가 붙은 테스트만 실행
./gradlew test -Dgroups=e2e

# 코드 스타일 + 단위 테스트 전체 검증 (PR 전 필수)
./gradlew check

# Playwright E2E 실행 (play_write 디렉터리에서)
npx playwright test
```
