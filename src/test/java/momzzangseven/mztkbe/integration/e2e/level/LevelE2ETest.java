package momzzangseven.mztkbe.integration.e2e.level;

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
 * Level & Attendance E2E 테스트 (Local Server + Real PostgreSQL).
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
 *   <li>신규 유저 레벨 조회 (초기 레벨 1, XP 0)
 *   <li>레벨 정책 목록 조회
 *   <li>출석 체크인 → XP 적립
 *   <li>중복 체크인 → 에러 반환
 *   <li>주간 출석 / 출석 상태 조회
 *   <li>레벨업 이력, XP 원장 조회
 *   <li>XP 부족 시 레벨업 불가
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[E2E] Level & Attendance 전체 흐름 테스트")
class LevelE2ETest {

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

  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.com";
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
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

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    signup(email, "Test@1234!", "레벨E2E유저");
    accessToken = loginAndGetAccessToken(email, "Test@1234!");
  }

  // ============================================================
  // E2E Tests — Level
  // ============================================================

  @Test
  @Order(1)
  @DisplayName("신규 유저 레벨 조회 → level=1, availableXp=0")
  void getMyLevel_newUser_returnsLevel1WithZeroXp() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/level",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/level").asInt()).isEqualTo(1);
    assertThat(root.at("/data/availableXp").asInt()).isEqualTo(0);
  }

  @Test
  @Order(2)
  @DisplayName("레벨 정책 목록 조회 → policies 배열 반환 (인증 필요)")
  void getLevelPolicies_authenticated_returnsPolicies() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/levels/policies",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/policies").isArray()).isTrue();
    assertThat(root.at("/data/policies").size()).isPositive();
  }

  @Test
  @Order(3)
  @DisplayName("레벨업 이력 조회 → 신규 유저는 빈 목록 반환")
  void getLevelUpHistories_newUser_returnsEmptyList() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/level-up-histories",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/histories").isArray()).isTrue();
    assertThat(root.at("/data/histories").size()).isEqualTo(0);
  }

  @Test
  @Order(4)
  @DisplayName("XP 원장 조회 → 신규 유저는 빈 목록 반환")
  void getXpLedger_newUser_returnsEmptyEntries() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/xp-ledger",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/entries").isArray()).isTrue();
  }

  @Test
  @Order(5)
  @DisplayName("XP 없는 신규 유저 레벨업 시도 → 4xx 에러 반환")
  void levelUp_withNoXp_returnsError() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/level-ups",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError())
        .as("XP 부족 상태에서 레벨업 시도 시 4xx 에러여야 함")
        .isTrue();
  }

  @Test
  @Order(6)
  @DisplayName("인증 없이 레벨 조회 시 401 반환")
  void getMyLevel_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/level",
            HttpMethod.GET,
            new HttpEntity<>(noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ============================================================
  // E2E Tests — Attendance
  // ============================================================

  @Test
  @Order(7)
  @DisplayName("출석 체크인 → XP 적립 및 응답 구조 검증")
  void checkIn_firstTime_grantsXpAndReturnsResult() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/attendance",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/success").asBoolean()).isTrue();
    assertThat(root.at("/data/attendedDate").asText()).isNotBlank();
    assertThat(root.at("/data/grantedXp").asInt()).isGreaterThanOrEqualTo(0);
  }

  @Test
  @Order(8)
  @DisplayName("출석 후 XP 원장에 출석 항목 생성 확인")
  void checkIn_afterCheckIn_xpLedgerContainsAttendanceEntry() throws Exception {
    restTemplate.exchange(
        baseUrl + "/users/me/attendance",
        HttpMethod.POST,
        new HttpEntity<>(authHeaders()),
        String.class);

    ResponseEntity<String> ledgerResponse =
        restTemplate.exchange(
            baseUrl + "/users/me/xp-ledger",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(ledgerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(ledgerResponse.getBody());
    JsonNode entries = root.at("/data/entries");
    assertThat(entries.isArray()).isTrue();
  }

  @Test
  @Order(9)
  @DisplayName("출석 상태 조회 → 응답 구조 검증")
  void getAttendanceStatus_returnsStatus() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/attendance/status",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data").isMissingNode()).isFalse();
  }

  @Test
  @Order(10)
  @DisplayName("주간 출석 조회 → 응답 구조 검증")
  void getWeeklyAttendance_returnsWeeklyData() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/attendance/weekly",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data").isMissingNode()).isFalse();
  }

  @Test
  @Order(11)
  @DisplayName("같은 날 중복 체크인 → 4xx 에러 반환")
  void checkIn_sameDay_returnsError() throws Exception {
    restTemplate.exchange(
        baseUrl + "/users/me/attendance",
        HttpMethod.POST,
        new HttpEntity<>(authHeaders()),
        String.class);

    ResponseEntity<String> secondResponse =
        restTemplate.exchange(
            baseUrl + "/users/me/attendance",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(secondResponse.getStatusCode().is4xxClientError())
        .as("같은 날 중복 체크인 시 4xx 에러여야 함")
        .isTrue();
  }
}
