package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Auth 토큰 라이프사이클 E2E 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml의 DB 설정 참조)
 *   <li>Flyway 마이그레이션이 자동으로 수행됨
 *   <li>.env 파일의 DB_URL, DB_USERNAME, DB_PASSWORD 환경 변수 사용
 * </ul>
 *
 * <p>MockMVC+H2 테스트와의 차이점:
 *
 * <ul>
 *   <li>실제 PostgreSQL 트랜잭션 커밋/롤백 검증
 *   <li>PESSIMISTIC_WRITE 락 동작 확인
 *   <li>토큰 회전(rotation) 원자성 검증
 *   <li>로그아웃 후 토큰 재사용 불가 확인
 * </ul>
 *
 * <p>외부 API(Kakao, Google)는 MockBean으로 대체 – LOCAL 인증 흐름만 테스트한다.
 */
@DisplayName("Auth 토큰 라이프사이클 E2E 테스트 (Local Server + PostgreSQL)")
class AuthTokenLifecycleE2ETest extends E2ETestBase {

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  // MarkTransactionSucceededService는 @ConditionalOnProperty(web3.reward-token.enabled=true)로
  // 테스트 환경에서 생성되지 않지만, MarkTransactionSucceededAdapter가 이를 주입하려 시도하므로
  // MockBean으로 컨텍스트 로딩 오류를 방지한다.
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port;
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  /** 매 테스트마다 고유한 이메일 생성 (데이터 충돌 방지). */
  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.com";
  }

  private ResponseEntity<String> signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    return restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private ResponseEntity<String> login(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    return restTemplate.exchange(
        baseUrl + "/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  /**
   * Set-Cookie 헤더에서 refreshToken 값만 추출한다.
   *
   * <p>Set-Cookie 예시: {@code refreshToken=eyJ...; Path=/auth; HttpOnly; Max-Age=604800}
   */
  private String extractRefreshToken(ResponseEntity<?> response) {
    String setCookieHeader = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(setCookieHeader).as("응답에 refreshToken Set-Cookie가 있어야 함").isNotNull();
    return setCookieHeader.split(";")[0].replace("refreshToken=", "").trim();
  }

  /** 응답 JSON의 $.data.accessToken 값을 추출한다. */
  private String extractAccessToken(ResponseEntity<String> response) throws Exception {
    JsonNode body = objectMapper.readTree(response.getBody());
    return body.at("/data/accessToken").asText();
  }

  private ResponseEntity<String> reissue(String refreshToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", "refreshToken=" + refreshToken);
    return restTemplate.exchange(
        baseUrl + "/auth/reissue", HttpMethod.POST, new HttpEntity<>(headers), String.class);
  }

  private ResponseEntity<Void> logout(String refreshToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", "refreshToken=" + refreshToken);
    return restTemplate.exchange(
        baseUrl + "/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @DisplayName("전체 라이프사이클: 회원가입 → 로그인 → 토큰 재발급 → 로그아웃")
  void fullLifecycle_signup_login_reissue_logout() throws Exception {
    String email = uniqueEmail();

    // 1. 회원가입
    ResponseEntity<String> signupResponse = signup(email, "Test@1234!", "E2E유저");
    assertThat(signupResponse.getStatusCode().is2xxSuccessful()).as("회원가입 성공 (2xx)").isTrue();

    // 2. 로그인
    ResponseEntity<String> loginResponse = login(email, "Test@1234!");
    assertThat(loginResponse.getStatusCode().is2xxSuccessful()).as("로그인 성공 (2xx)").isTrue();
    String refreshToken = extractRefreshToken(loginResponse);
    assertThat(refreshToken).isNotBlank();

    // 3. 토큰 재발급
    ResponseEntity<String> reissueResponse = reissue(refreshToken);
    assertThat(reissueResponse.getStatusCode().is2xxSuccessful()).as("토큰 재발급 성공 (2xx)").isTrue();
    String newRefreshToken = extractRefreshToken(reissueResponse);
    assertThat(newRefreshToken)
        .as("재발급된 refreshToken은 기존과 달라야 함 (토큰 회전)")
        .isNotEqualTo(refreshToken);

    // 4. 새 토큰으로 로그아웃
    ResponseEntity<Void> logoutResponse = logout(newRefreshToken);
    assertThat(logoutResponse.getStatusCode().value()).as("로그아웃 성공 (204)").isEqualTo(204);
  }

  @Test
  @DisplayName("토큰 회전: 재발급 후 이전 refreshToken은 무효화되어야 함 (DB 커밋 검증)")
  void tokenRotation_afterReissue_oldTokenIsRevoked() throws Exception {
    String email = uniqueEmail();
    signup(email, "Test@1234!", "E2E유저");

    ResponseEntity<String> loginResponse = login(email, "Test@1234!");
    String originalRefreshToken = extractRefreshToken(loginResponse);

    // 재발급 – 원본 토큰이 revoked 됨
    ResponseEntity<String> firstReissue = reissue(originalRefreshToken);
    assertThat(firstReissue.getStatusCode().is2xxSuccessful()).as("첫 번째 재발급 성공").isTrue();

    // 이전 토큰으로 다시 재발급 시도 → 실패 (revoked 상태)
    ResponseEntity<String> secondReissue = reissue(originalRefreshToken);
    assertThat(secondReissue.getStatusCode().is4xxClientError())
        .as("만료된(revoked) 토큰으로 재발급 시도 → 4xx 에러")
        .isTrue();
  }

  @Test
  @DisplayName("로그아웃 후 refreshToken 재사용 불가 (DB 커밋 검증)")
  void afterLogout_revokedToken_cannotBeReused() throws Exception {
    String email = uniqueEmail();
    signup(email, "Test@1234!", "E2E유저");

    ResponseEntity<String> loginResponse = login(email, "Test@1234!");
    String refreshToken = extractRefreshToken(loginResponse);

    // 로그아웃
    ResponseEntity<Void> logoutResponse = logout(refreshToken);
    assertThat(logoutResponse.getStatusCode().value()).as("로그아웃 성공 (204)").isEqualTo(204);

    // 로그아웃된 토큰으로 재발급 시도 → 실패
    ResponseEntity<String> reissueAfterLogout = reissue(refreshToken);
    assertThat(reissueAfterLogout.getStatusCode().is4xxClientError())
        .as("로그아웃 후 revoked 토큰으로 재발급 시도 → 4xx 에러")
        .isTrue();
  }

  @Test
  @DisplayName("토큰 재발급 체인: 재발급 → 재발급 (새 토큰으로 연속 재발급 가능)")
  void tokenChain_multipleReissues_eachSucceeds() throws Exception {
    String email = uniqueEmail();
    signup(email, "Test@1234!", "E2E유저");

    ResponseEntity<String> loginResponse = login(email, "Test@1234!");
    String token = extractRefreshToken(loginResponse);

    // 3회 연속 재발급 (각 재발급마다 새 토큰 사용)
    for (int i = 1; i <= 3; i++) {
      ResponseEntity<String> reissueResponse = reissue(token);
      assertThat(reissueResponse.getStatusCode().is2xxSuccessful()).as(i + "번째 재발급 성공").isTrue();
      String nextToken = extractRefreshToken(reissueResponse);
      assertThat(nextToken).as(i + "번째 재발급 토큰은 이전과 달라야 함").isNotEqualTo(token);
      token = nextToken;
    }
  }

  @Test
  @DisplayName("로그인 성공 응답 구조 검증: accessToken, userInfo 포함")
  void login_responseStructure_containsExpectedFields() throws Exception {
    String email = uniqueEmail();
    signup(email, "Test@1234!", "E2E유저");

    ResponseEntity<String> loginResponse = login(email, "Test@1234!");
    assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode body = objectMapper.readTree(loginResponse.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(body.at("/data/accessToken").asText()).isNotBlank();
    assertThat(body.at("/data/grantType").asText()).isEqualTo("Bearer");
    assertThat(body.at("/data/expiresIn").asLong()).isPositive();
    assertThat(body.at("/data/userInfo/email").asText()).isEqualTo(email);
    assertThat(body.at("/data/userInfo/userId").asLong()).isPositive();
  }
}
