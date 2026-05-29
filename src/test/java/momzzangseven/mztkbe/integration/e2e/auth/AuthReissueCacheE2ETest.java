package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.hibernate.SessionFactory;
import org.hibernate.stat.EntityStatistics;
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
 * <p>커버하는 contract:
 *
 * <ul>
 *   <li>(1) 같은 userId 에 대한 연속 reissue 에서 두 번째 호출은 UserAccount fetch 가 사라진다 (pure cache hit).
 *   <li>(2) 로그인의 {@code save(updateLastLogin)} 이 발행하는 {@code UserAccountInvalidatedEvent} 가
 *       AFTER_COMMIT 에서 캐시를 evict 한다.
 *   <li>(3) BLOCKED 로 명시적 save 후 reissue 가 403 으로 거부된다 (HTTP 경로의 invalidation 입증).
 * </ul>
 *
 * <p>측정: Hibernate Statistics 의 {@link UserAccountEntity} entity-scoped fetch count delta. reissue
 * 가 발사하는 다른 SQL (refresh_token UPDATE/INSERT 등) 은 다른 엔티티라 신호를 오염시키지 않으며, 글로벌 스케줄러의 JDBC 트래픽도 entity
 * scope 밖이라 영향이 없다.
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
  @Autowired private CheckAccountStatusUseCase checkAccountStatusUseCase;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private Statistics statistics;

  @BeforeEach
  void enableStatistics() {
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  /**
   * Pure cache-hit 검증 — reissue1 은 cache miss 라 fetch 가 1회 발생, reissue2 는 cache hit 이라 fetch 가 0회.
   *
   * <p>주의: 이 테스트는 새로 만든 userId 에 대해 cache 가 비어있다는 점에 의존하므로 구조적으로 reissue1=MISS, reissue2=HIT 가
   * 보장된다. 즉 "로그인이 cache 를 invalidate 하는지" 는 이 테스트로 입증되지 않는다 — 그 contract 는 {@link
   * #login_invalidatesUserAccountStatusCache()} 가 커버한다. BLOCK save 경로의 invalidation 은 {@link
   * #reissueAfterAccountBlocked_returns403_provingInvalidation()} 가 커버한다.
   */
  @Test
  @DisplayName(
      "/auth/reissue 연속 호출 — 2번째는 UserAccount fetch 0회 (pure cache hit; invalidation 검증은 별도 테스트)")
  void reissueTwice_secondCallSavesOneUserAccountFetch() throws Exception {
    String email = uniqueEmail();
    signup(email, DEFAULT_TEST_PASSWORD, "reissue-cache");
    ResponseEntity<String> login = login(email, DEFAULT_TEST_PASSWORD);
    String refreshToken1 = extractRefreshToken(login);

    long beforeFirst = userAccountFetchCount();
    ResponseEntity<String> reissue1 = reissue(refreshToken1);
    assertThat(reissue1.getStatusCode().is2xxSuccessful()).isTrue();
    long afterFirst = userAccountFetchCount();
    long firstReissueFetches = afterFirst - beforeFirst;

    String refreshToken2 = extractRefreshToken(reissue1);
    ResponseEntity<String> reissue2 = reissue(refreshToken2);
    assertThat(reissue2.getStatusCode().is2xxSuccessful()).isTrue();
    long afterSecond = userAccountFetchCount();
    long secondReissueFetches = afterSecond - afterFirst;

    assertThat(firstReissueFetches)
        .as("1st reissue 는 UserAccount fetch 가 최소 1회 발생해야 함 (cache miss)")
        .isEqualTo(1L);
    assertThat(secondReissueFetches)
        .as(
            "2nd reissue 는 UserAccount fetch 가 0회여야 함 (cache hit). 1st=%d, 2nd=%d",
            firstReissueFetches, secondReissueFetches)
        .isZero();
  }

  /**
   * Login 의 {@code save(updateLastLogin)} 이 {@code UserAccountInvalidatedEvent} 를 발행해 AFTER_COMMIT
   * 에서 cache 를 evict 하는지를 입증한다.
   *
   * <p>흐름: signup → 1차 login → findStatus 로 cache prewarm → 2차 login (save → invalidate) →
   * findStatus 가 다시 fetch 를 일으켜야 한다. login 자체의 SQL 은 fetch counter 측정 직전에 {@link
   * Statistics#clear()} 로 격리한다.
   */
  @Test
  @DisplayName(
      "login 의 save(updateLastLogin) → AFTER_COMMIT 이벤트 → 캐시 evict → 다음 findStatus 가 fetch 1회 발생")
  void login_invalidatesUserAccountStatusCache() throws Exception {
    String email = uniqueEmail();
    signup(email, DEFAULT_TEST_PASSWORD, "login-inv");
    ResponseEntity<String> firstLogin = login(email, DEFAULT_TEST_PASSWORD);
    assertThat(firstLogin.getStatusCode().is2xxSuccessful()).isTrue();
    Long userId = extractUserId(firstLogin);

    // Cache prewarm — 다음 findStatus 가 fetch 를 일으키지 않게 cache 에 항목을 채운다.
    checkAccountStatusUseCase.findStatus(userId);

    // 2차 login — save(updateLastLogin) → UserAccountInvalidatedEvent → AFTER_COMMIT 에서 evict.
    ResponseEntity<String> secondLogin = login(email, DEFAULT_TEST_PASSWORD);
    assertThat(secondLogin.getStatusCode().is2xxSuccessful()).isTrue();

    // login flow 자체가 발사한 fetch 는 측정 신호에서 제거 — 검증 대상은
    // 'invalidate 되었으므로 다음 findStatus 가 다시 fetch 를 일으킨다' 뿐.
    statistics.clear();
    long baseline = userAccountFetchCount();
    checkAccountStatusUseCase.findStatus(userId);
    long afterFindStatus = userAccountFetchCount();

    assertThat(afterFindStatus - baseline)
        .as("login save 가 cache 를 evict 했어야 함 — findStatus 가 다시 DB 를 fetch 해야 한다")
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
  // Measurement helpers
  // ============================================================

  /**
   * {@link UserAccountEntity} 가 DB 에서 읽혀온 횟수. Hibernate 의 {@link EntityStatistics#getLoadCount()}
   * (일반 find/JPQL) 와 {@link EntityStatistics#getFetchCount()} (lazy initialization) 를 합산해 "이 entity
   * 가 DB 에 한 번이라도 hit 했나" 를 측정한다. {@link Statistics#getEntityNames()} 에서 등록된 이름을 lookup 해 환경별
   * simple/FQN 차이를 흡수한다.
   */
  private long userAccountFetchCount() {
    EntityStatistics es = statistics.getEntityStatistics(userAccountEntityName());
    return es.getLoadCount() + es.getFetchCount();
  }

  private String userAccountEntityName() {
    String fqn = UserAccountEntity.class.getName();
    String simple = UserAccountEntity.class.getSimpleName();
    for (String name : statistics.getEntityNames()) {
      if (name.equals(fqn) || name.equals(simple) || name.endsWith("." + simple)) {
        return name;
      }
    }
    throw new IllegalStateException(
        "UserAccountEntity not registered in Hibernate Statistics. Registered: "
            + java.util.Arrays.toString(statistics.getEntityNames()));
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
