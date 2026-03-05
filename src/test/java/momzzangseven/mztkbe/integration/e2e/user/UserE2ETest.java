package momzzangseven.mztkbe.integration.e2e.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

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

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;

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
    return objectMapper.readTree(response.getBody()).at("/data/stepUpToken").asText();
  }

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    signup(email, TEST_PASSWORD, "유저E2E테스터");
    accessToken = loginAndGetAccessToken(email, TEST_PASSWORD);
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
  @DisplayName("역할 변경 → 사용자 정보 필드 응답 구조 검증")
  void updateRole_returnsCompleteUserInfo() throws Exception {
    Map<String, String> body = Map.of("role", "USER");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/role",
            HttpMethod.PATCH,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
    assertThat(data.at("/id").asLong()).isPositive();
    assertThat(data.at("/email").asText()).isNotBlank();
    assertThat(data.at("/nickname").asText()).isNotBlank();
    assertThat(data.at("/role").asText()).isEqualTo("USER");
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
}
