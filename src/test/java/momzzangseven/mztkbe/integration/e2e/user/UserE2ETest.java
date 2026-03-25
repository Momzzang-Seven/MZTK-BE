package momzzangseven.mztkbe.integration.e2e.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.user.application.service.WithdrawalHardDeleteService;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * User E2E 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml 참조)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>테스트 시나리오:
 *
 * <ul>
 *   <li>역할 수정 (USER → TRAINER)
 *   <li>잘못된 역할 값으로 수정 시 400 반환
 *   <li>인증 없이 접근 시 401 반환
 *   <li>Step-Up 인증 후 회원 탈퇴 전체 흐름
 *   <li>Step-Up 토큰 없이 탈퇴 시도 → 403 반환
 *   <li>탈퇴(soft delete) 후 연결된 location.deleted_at 설정 확인
 *   <li>Hard delete 실행 후 soft-deleted location 레코드 완전 삭제 확인
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[E2E] User 전체 흐름 테스트")
class UserE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WithdrawalHardDeleteService withdrawalHardDeleteService;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;
  private String currentUserEmail;

  // ============================================================
  // Helper Methods
  // ============================================================

  private static final String TEST_PASSWORD = "Test@1234!";

  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.com";
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private HttpHeaders bearerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return headers;
  }

  private void signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  /**
   * 회원가입 후 signup 응답에서 userId 를 추출합니다.
   *
   * <p>Hard delete 후 cascade 검증에서 특정 userId 기준으로 DB 를 조회할 때 사용합니다.
   */
  private Long signupAndGetUserId(String email, String password, String nickname) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/signup",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    return objectMapper.readTree(response.getBody()).at("/data/userId").asLong();
  }

  /**
   * 테스트용 위치를 등록하고 locationId 를 반환합니다.
   *
   * <p>location cascade 검증의 사전 조건으로 사용합니다.
   */
  private Long registerTestLocation(String token) throws Exception {
    Map<String, Object> body =
        Map.of(
            "locationName", "테스트 위치",
            "postalCode", "12345",
            "address", "서울특별시 강남구 테헤란로 1",
            "detailAddress", "1층",
            "latitude", 37.5012,
            "longitude", 127.0396);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders(token)),
            String.class);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("위치 등록 성공 (2xx) 이어야 함: " + response.getBody())
        .isTrue();
    return objectMapper.readTree(response.getBody()).at("/data/locationId").asLong();
  }

  /**
   * Step-Up 인증을 수행하고 Step-Up Access Token을 반환합니다.
   *
   * <p>회원 탈퇴와 같은 민감한 작업에 필요한 {@code ROLE_STEP_UP} 토큰을 발급합니다.
   */
  private String performStepUpAndGetToken(String password) throws Exception {
    Map<String, Object> body = Map.of("password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/stepup",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("Step-Up 인증 성공 (2xx) 이어야 함: " + response.getBody())
        .isTrue();
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    currentUserEmail = uniqueEmail();
    signup(currentUserEmail, TEST_PASSWORD, "유저E2E테스터");
    accessToken = loginAndGetAccessToken(currentUserEmail, TEST_PASSWORD);
  }

  @AfterEach
  void tearDown() {
    // 1. 현재 테스트 유저의 위치 데이터 삭제 (FK: locations.user_id → users.id)
    //    withdrawal 후 soft-deleted 상태인 location 도 포함하여 삭제
    jdbcTemplate.update(
        "DELETE FROM locations WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        currentUserEmail);

    // 2. 현재 테스트 유저 삭제 (soft-deleted 상태여도 완전 삭제)
    //    hard delete 로 이미 삭제된 경우 0건 처리되므로 안전함
    jdbcTemplate.update("DELETE FROM users WHERE email = ?", currentUserEmail);
  }

  // ============================================================
  // E2E Tests — Role Update
  // ============================================================

  @Test
  @Order(1)
  @DisplayName("역할 변경 (USER → TRAINER) → 200 및 변경된 role 반환")
  void updateRole_userToTrainer_returns200WithNewRole() throws Exception {
    Map<String, String> body = Map.of("role", "TRAINER");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/role",
            HttpMethod.PATCH,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/role").asText()).isEqualTo("TRAINER");
    assertThat(root.at("/data/id").asLong()).isPositive();
  }

  @Test
  @Order(2)
  @DisplayName("현재와 같은 역할로 변경 → 400 검증")
  void updateRole_returnsCompleteUserInfo() throws Exception {
    Map<String, String> body = Map.of("role", "USER");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/role",
            HttpMethod.PATCH,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @Order(3)
  @DisplayName("잘못된 역할 값으로 변경 시 400 반환")
  void updateRole_withInvalidRole_returns400() {
    Map<String, String> body = Map.of("role", "SUPER_ADMIN");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/role",
            HttpMethod.PATCH,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @Order(4)
  @DisplayName("role 없이 요청 시 400 반환")
  void updateRole_withNullRole_returns400() {
    Map<String, Object> body = Map.of();

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/role",
            HttpMethod.PATCH,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @Order(5)
  @DisplayName("인증 없이 역할 변경 시 401 반환")
  void updateRole_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("role", "TRAINER");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/role",
            HttpMethod.PATCH,
            new HttpEntity<>(body, noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ============================================================
  // E2E Tests — Withdrawal
  // ============================================================

  @Test
  @Order(6)
  @DisplayName("일반 Access Token으로 탈퇴 시도 → 403 반환 (ROLE_STEP_UP 필요)")
  void withdrawal_withNormalToken_returns403() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/withdrawal",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @Order(7)
  @DisplayName("Step-Up 인증 후 회원 탈퇴 전체 흐름 → 200 반환")
  void withdrawal_afterStepUp_returns200() throws Exception {
    String stepUpToken = performStepUpAndGetToken(TEST_PASSWORD);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/withdrawal",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders(stepUpToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
  }

  // ============================================================
  // E2E Tests — Location Cascade (Withdrawal)
  //
  // LocationUserSoftDeleteEventHandler: 탈퇴 이벤트 수신 → location.deleted_at 설정
  // LocationUserHardDeleteEventHandler: hard delete 이벤트 수신 → location 물리 삭제
  //
  // WithdrawalHardDeleteService.runBatch()를 Spring Bean 으로 직접 호출하여
  // 스케줄러(@Scheduled) 없이 hard delete cascade 를 검증합니다.
  // ============================================================

  @Test
  @Order(8)
  @DisplayName("탈퇴(soft delete) 후 연결된 location.deleted_at 이 설정되어야 한다")
  void withdrawal_softDelete_setsLocationDeletedAt() throws Exception {
    // 위치 등록
    Long locationId = registerTestLocation(accessToken);

    // step-up → 탈퇴 (UserSoftDeletedEvent 발행 → LocationUserSoftDeleteEventHandler 실행)
    String stepUpToken = performStepUpAndGetToken(TEST_PASSWORD);
    ResponseEntity<String> withdrawRes =
        restTemplate.exchange(
            baseUrl + "/users/me/withdrawal",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders(stepUpToken)),
            String.class);
    assertThat(withdrawRes.getStatusCode()).isEqualTo(HttpStatus.OK);

    // DB 확인: LocationUserSoftDeleteEventHandler 가 deleted_at 을 현재 시간으로 설정
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM locations WHERE id = ? AND deleted_at IS NOT NULL",
            Integer.class,
            locationId);
    assertThat(count).as("탈퇴 후 location.deleted_at 이 설정되어야 한다").isEqualTo(1);
  }

  @Test
  @Order(9)
  @DisplayName("Hard delete 실행 후 soft-deleted location 레코드가 완전 삭제되어야 한다")
  void withdrawal_hardDelete_physicallyCleansUpLocations() throws Exception {
    // 독립적인 사용자 생성 (hard delete 가 다른 테스트 사용자에게 영향을 주지 않도록)
    String email = uniqueEmail();
    Long userId = signupAndGetUserId(email, TEST_PASSWORD, "하드삭제위치테스터");
    accessToken = loginAndGetAccessToken(email, TEST_PASSWORD);

    // 위치 등록
    Long locationId = registerTestLocation(accessToken);

    // step-up → 탈퇴 (soft delete)
    String stepUpToken = performStepUpAndGetToken(TEST_PASSWORD);
    ResponseEntity<String> withdrawRes =
        restTemplate.exchange(
            baseUrl + "/users/me/withdrawal",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders(stepUpToken)),
            String.class);
    assertThat(withdrawRes.getStatusCode()).isEqualTo(HttpStatus.OK);

    // soft delete 결과 사전 확인: location.deleted_at IS NOT NULL
    Integer softDeletedCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM locations WHERE id = ? AND deleted_at IS NOT NULL",
            Integer.class,
            locationId);
    assertThat(softDeletedCount).as("탈퇴 후 location 이 soft-deleted 상태여야 한다").isEqualTo(1);

    // Hard delete 실행
    // now.plusDays(999) 를 전달하면 cutoff = now+999-retentionDays 가 미래가 되어
    // 방금 soft-delete 된 사용자도 즉시 배치 대상에 포함됩니다.
    withdrawalHardDeleteService.runBatch(LocalDateTime.now().plusDays(999));

    // DB 확인: LocationUserHardDeleteEventHandler 가 soft-deleted location 을 물리 삭제
    Integer remaining =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM locations WHERE user_id = ?", Integer.class, userId);
    assertThat(remaining).as("hard delete 후 user_locations 레코드가 완전히 삭제되어야 한다").isEqualTo(0);
  }
}
