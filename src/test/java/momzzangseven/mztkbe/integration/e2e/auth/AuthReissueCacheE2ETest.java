package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * [MOM-460] {@code /auth/reissue} 엔드포인트의 실제 HTTP 경로에서 Caffeine 캐시가 동작하는지, 그리고 계정에 write 가 발생하면
 * invalidation 이 이뤄지는지 검증한다.
 *
 * <p>{@link AuthStatusCacheE2ETest} 는 use case 를 직접 호출해 측정한 반면, 본 테스트는 운영에서 실제로 풀 압박을 만든 진앙 경로 —
 * POST /auth/reissue → {@code ReissueTokenService.validateRefreshSubject} → {@code
 * LoadUserAccountPort.findByUserId} — 가 캐시에 흡수되는지를 입증한다.
 *
 * <p>측정: Hibernate Statistics 의 {@code UserAccountEntity} fetch count delta. reissue 가 발사하는 다른 SQL
 * (refresh_token UPDATE/INSERT 등) 은 다른 엔티티라 신호를 오염시키지 않는다.
 */
@DisplayName("[E2E] /auth/reissue Caffeine cache + write invalidation (MOM-460)")
@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "mztk.admin.bootstrap.enabled=false"
    })
class AuthReissueCacheE2ETest extends E2ETestBase {

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @Autowired private LoadUserAccountPort loadUserAccountPort;
  @Autowired private SaveUserAccountPort saveUserAccountPort;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private Statistics statistics;

  @BeforeEach
  void enableStatistics() {
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  @Test
  @DisplayName(
      "/auth/reissue 연속 호출 — 2번째가 1번째보다 JPQL 1개 적게 발사 (cache hit 으로 UserAccount SELECT 절감)")
  void reissueTwice_secondCallSavesOneQuery() throws Exception {
    String email = uniqueEmail();
    signup(email, DEFAULT_TEST_PASSWORD, "reissue-cache");
    ResponseEntity<String> login = login(email, DEFAULT_TEST_PASSWORD);
    String refreshToken1 = extractRefreshToken(login);

    long beforeFirst = statistics.getQueryExecutionCount();
    ResponseEntity<String> reissue1 = reissue(refreshToken1);
    assertThat(reissue1.getStatusCode().is2xxSuccessful()).isTrue();
    long afterFirst = statistics.getQueryExecutionCount();
    long firstReissueQueries = afterFirst - beforeFirst;

    String refreshToken2 = extractRefreshToken(reissue1);
    ResponseEntity<String> reissue2 = reissue(refreshToken2);
    assertThat(reissue2.getStatusCode().is2xxSuccessful()).isTrue();
    long afterSecond = statistics.getQueryExecutionCount();
    long secondReissueQueries = afterSecond - afterFirst;

    assertThat(firstReissueQueries)
        .as("1st reissue: refresh-token rotation + UserAccount SELECT 등 다수 query")
        .isPositive();
    assertThat(firstReissueQueries - secondReissueQueries)
        .as(
            "2nd reissue 는 UserAccount SELECT 1개 만큼 query 가 줄어야 함 (cache hit). " + "1st=%d, 2nd=%d",
            firstReissueQueries, secondReissueQueries)
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("/auth/reissue 후 계정 BLOCK write → AFTER_COMMIT 이벤트로 invalidate → 다음 reissue 는 403")
  void reissueAfterAccountBlocked_returns403_provingInvalidation() throws Exception {
    String email = uniqueEmail();
    signup(email, DEFAULT_TEST_PASSWORD, "invalidation-user");
    ResponseEntity<String> login = login(email, DEFAULT_TEST_PASSWORD);
    String refreshToken1 = extractRefreshToken(login);
    Long userId = extractUserId(login);

    ResponseEntity<String> reissue1 = reissue(refreshToken1);
    assertThat(reissue1.getStatusCode().is2xxSuccessful()).isTrue();
    String refreshToken2 = extractRefreshToken(reissue1);

    UserAccount loaded = loadUserAccountPort.findByUserId(userId).orElseThrow();
    saveUserAccountPort.save(loaded.changeManagedStatus(AccountStatus.BLOCKED));

    ResponseEntity<String> reissueAfterBlock = reissue(refreshToken2);

    assertThat(reissueAfterBlock.getStatusCode())
        .as("캐시가 invalidate 되지 않았다면 cached ACTIVE 상태로 200 이 반환됨 — 403 이라야 invalidation 입증")
        .isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = objectMapper.readTree(reissueAfterBlock.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("USER_006");
  }

  // ============================================================
  // HTTP helpers — Cookie-based reissue (AuthTokenLifecycleE2ETest 와 동일 패턴)
  // ============================================================

  private static String uniqueEmail() {
    return "reissue-cache-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
  }

  private ResponseEntity<String> signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    return restTemplate.exchange(
        baseUrl() + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private ResponseEntity<String> login(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    return restTemplate.exchange(
        baseUrl() + "/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private ResponseEntity<String> reissue(String refreshToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", "refreshToken=" + refreshToken);
    return restTemplate.exchange(
        baseUrl() + "/auth/reissue", HttpMethod.POST, new HttpEntity<>(headers), String.class);
  }

  private String extractRefreshToken(ResponseEntity<?> response) {
    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).as("응답에 refreshToken Set-Cookie 가 있어야 함").isNotNull();
    return setCookie.split(";")[0].replace("refreshToken=", "").trim();
  }

  private Long extractUserId(ResponseEntity<String> response) throws Exception {
    JsonNode body = objectMapper.readTree(response.getBody());
    return body.at("/data/userInfo/userId").asLong();
  }
}
