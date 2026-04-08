package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * 회원가입 시 role 설정 E2E 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml의 DB 설정 참조)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>테스트 시나리오:
 *
 * <ul>
 *   <li>LOCAL 회원가입 시 role=USER 명시 → USER role 저장
 *   <li>LOCAL 회원가입 시 role=TRAINER 명시 → TRAINER role 저장
 *   <li>LOCAL 회원가입 시 role 미전달 → 기본값 USER 저장
 *   <li>LOCAL 회원가입 시 role=ADMIN → 에러 반환, DB에 레코드 없음
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] 회원가입 role 설정 테스트")
class SignupRoleE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private final List<String> createdUserEmails = new ArrayList<>();

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port;
  }

  @AfterEach
  void tearDown() {
    for (String email : createdUserEmails) {
      jdbcTemplate.update(
          "DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update(
          "DELETE FROM user_progress WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update(
          "DELETE FROM users_account WHERE user_id = (SELECT id FROM users WHERE email = ?)",
          email);
      jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }
    createdUserEmails.clear();
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.com";
  }

  private ResponseEntity<String> signup(String email, String password, String nickname) {
    createdUserEmails.add(email);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    return restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private ResponseEntity<String> signupWithRole(
      String email, String password, String nickname, String role) {
    createdUserEmails.add(email);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body =
        Map.of("email", email, "password", password, "nickname", nickname, "role", role);
    return restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  /**
   * role이 null인 경우를 테스트하기 위해 Map에 role 키를 포함하지 않는 요청을 보낸다. Map.of()는 null value를 허용하지 않으므로 HashMap을
   * 사용한다.
   */
  private ResponseEntity<String> signupWithNullRole(
      String email, String password, String nickname) {
    createdUserEmails.add(email);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = new HashMap<>();
    body.put("email", email);
    body.put("password", password);
    body.put("nickname", nickname);
    body.put("role", null);
    return restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private String queryUserRole(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT role FROM users WHERE email = ?", String.class, email);
  }

  private boolean userExists(String email) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
    return count != null && count > 0;
  }

  // ============================================================
  // E2E Tests — LOCAL 회원가입 role 설정
  // ============================================================

  @Nested
  @DisplayName("LOCAL 회원가입 role 설정")
  class LocalSignupRole {

    @Test
    @DisplayName("[E-1] role=USER 명시 시 USER role로 생성")
    void signup_withUserRole_createsUserWithUserRole() throws Exception {
      String email = uniqueEmail();

      ResponseEntity<String> response = signupWithRole(email, "Test@1234!", "테스터USER", "USER");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = objectMapper.readTree(response.getBody());
      assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(body.at("/data/role").asText()).isEqualTo("USER");

      // DB 검증
      assertThat(queryUserRole(email)).isEqualTo("USER");
    }

    @Test
    @DisplayName("[E-2] role=TRAINER 명시 시 TRAINER role로 생성")
    void signup_withTrainerRole_createsUserWithTrainerRole() throws Exception {
      String email = uniqueEmail();

      ResponseEntity<String> response =
          signupWithRole(email, "Test@1234!", "테스터TRAINER", "TRAINER");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = objectMapper.readTree(response.getBody());
      assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(body.at("/data/role").asText()).isEqualTo("TRAINER");

      // DB 검증
      assertThat(queryUserRole(email)).isEqualTo("TRAINER");
    }

    @Test
    @DisplayName("[E-3] role 미전달 시 기본값 USER로 생성")
    void signup_withoutRole_defaultsToUser() throws Exception {
      String email = uniqueEmail();

      ResponseEntity<String> response = signup(email, "Test@1234!", "기본역할유저");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = objectMapper.readTree(response.getBody());
      assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(body.at("/data/role").asText()).isEqualTo("USER");

      // DB 검증
      assertThat(queryUserRole(email)).isEqualTo("USER");
    }

    @Test
    @DisplayName("[E-3b] role=null 명시적 전달 시 기본값 USER로 생성")
    void signup_withExplicitNullRole_defaultsToUser() throws Exception {
      String email = uniqueEmail();

      ResponseEntity<String> response = signupWithNullRole(email, "Test@1234!", "널역할유저");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = objectMapper.readTree(response.getBody());
      assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(body.at("/data/role").asText()).isEqualTo("USER");

      // DB 검증
      assertThat(queryUserRole(email)).isEqualTo("USER");
    }

    @Test
    @DisplayName("[E-4] role=ADMIN 시 에러 반환 및 DB에 유저 미생성")
    void signup_withAdminRole_returnsErrorAndNoDbRecord() throws Exception {
      String email = uniqueEmail();

      ResponseEntity<String> response = signupWithRole(email, "Test@1234!", "어드민시도", "ADMIN");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

      // DB 검증: 유저가 생성되지 않아야 함 (트랜잭션 롤백)
      assertThat(userExists(email)).isFalse();
    }

    @Test
    @DisplayName("[E-5] 회원가입 응답 구조 검증: userId, email, nickname, role 포함")
    void signup_responseStructure_containsExpectedFields() throws Exception {
      String email = uniqueEmail();

      ResponseEntity<String> response = signupWithRole(email, "Test@1234!", "응답구조확인", "TRAINER");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = objectMapper.readTree(response.getBody());
      assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(body.at("/data/userId").asLong()).isPositive();
      assertThat(body.at("/data/email").asText()).isEqualTo(email);
      assertThat(body.at("/data/nickname").asText()).isEqualTo("응답구조확인");
      assertThat(body.at("/data/role").asText()).isEqualTo("TRAINER");
    }
  }
}
