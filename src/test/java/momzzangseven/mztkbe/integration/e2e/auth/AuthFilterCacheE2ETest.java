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
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.entity.AdminAccountEntity;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * [MOM-460] JwtAuthenticationFilter 의 hot path — {@code checkAccountStatusUseCase.isActive /
 * isDeleted / isBlocked} 와 admin 측의 {@code checkAdminAccountStatusUseCase.isActiveAdmin} — 가
 * Caffeine cache 로 흡수되어 인증된 매 요청마다 DB 를 치지 않음을 HTTP 레벨로 검증한다.
 *
 * <p>{@code AuthStatusCacheE2ETest} 는 use case 를 직접 호출해 cache 동작만 측정했고, {@code
 * AuthReissueCacheE2ETest} 는 {@code /auth/reissue} (filter 가 access token 부재로 early bail out 하는 경로)
 * 를 검증했다. 본 테스트는 <b>인증된 일반 endpoint 요청</b> — 즉 prod 트래픽의 절대다수 형태 — 의 filter 단계가 cache 에 흡수되는지를
 * 입증한다.
 *
 * <p>커버하는 contract:
 *
 * <ul>
 *   <li>(1) 같은 access token 으로 {@code GET /users/me} 연속 호출 시 두 번째는 filter 가 발사하는 {@link
 *       UserAccountEntity} fetch 가 0 회 (pure cache hit).
 *   <li>(2) {@link AccountStatus#BLOCKED} 로 직접 save → AFTER_COMMIT 이 cache 를 evict → 다음 {@code GET
 *       /users/me} 즉시 403 (cache stale 방어).
 *   <li>(3) admin token 으로 {@code GET /admin/dashboard/user-stats} 연속 호출 시 두 번째는 filter 가 발사하는
 *       {@link AdminAccountEntity} fetch 가 0 회 — admin 측 동일 cache contract 가 filter 경유로도 동작함을 확인.
 * </ul>
 *
 * <p>측정 원리: Hibernate Statistics 의 entity-scoped load/fetch count delta.
 *
 * <ul>
 *   <li>{@code /users/me} 의 핸들러 {@code GetMyProfileService} 는 {@code GetAuthProviderService} 경유로
 *       {@link UserAccountEntity} 를 1 회 fetch 한다 (cache 미적용 경로). 따라서 filter 와 핸들러가 같은 entity 를
 *       건드린다. 본 테스트는 절대값 1/0 대신 <b>{@code firstDelta - secondDelta == 1}</b> 의 delta-of-delta 로
 *       filter 의 cache 절감만을 분리해 측정한다 — 핸들러가 매 요청에 동일하게 1 회 fetch 하므로 그 항은 양변에서 상쇄되고, filter 의
 *       miss(1) vs hit(0) 차이만 남는다.
 *   <li>{@code /admin/dashboard/user-stats} 의 {@code GetAdminUserStatsService} 는 통계 집계 쿼리만 발사하고
 *       {@link AdminAccountEntity} 를 추가로 fetch 하지 않으며, {@code @AdminOnly} 부수 효과 (audit write) 도 별
 *       entity 라 측정 신호 밖. 따라서 admin 측은 절대값 1/0 assertion 이 그대로 성립.
 * </ul>
 */
@DisplayName("[E2E] JwtAuthenticationFilter Caffeine cache + invalidation (MOM-460)")
@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "mztk.admin.bootstrap.enabled=false"
    })
class AuthFilterCacheE2ETest extends E2ETestBase {

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @Autowired private LoadUserAccountPort loadUserAccountPort;
  @Autowired private SaveUserAccountPort saveUserAccountPort;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  private Statistics statistics;

  @BeforeEach
  void enableStatistics() {
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  // ============================================================
  // Scenario (1) — User filter path: 2nd GET /users/me cache hit
  // ============================================================

  @Test
  @DisplayName("GET /users/me 연속 호출 — 2번째는 filter 가 UserAccountEntity fetch 0회 (cache hit)")
  void authenticatedGetMe_twice_secondCallFilterIssuesNoUserAccountFetch() {
    TestUser user = signupAndLogin("filter-cache-user");
    HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(user.accessToken()));

    // signup/login 이 발사한 fetch 는 본 검증 대상이 아님 — baseline 리셋.
    statistics.clear();
    long beforeFirst = userAccountFetchCount();

    ResponseEntity<String> first =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
    long firstDelta = userAccountFetchCount() - beforeFirst;

    long beforeSecond = userAccountFetchCount();
    ResponseEntity<String> second =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
    long secondDelta = userAccountFetchCount() - beforeSecond;

    // /users/me 핸들러도 UserAccountEntity 를 1회 fetch (GetAuthProviderService 경유, cache 비대상).
    // 양 요청 모두에 핸들러 fetch 가 동일하게 발생하므로, filter 의 cache 절감만 분리하려면
    // delta-of-delta = 1 (filter miss=1 → hit=0 의 차이) 로 측정한다.
    assertThat(firstDelta)
        .as("1st GET /users/me — filter cache miss + 핸들러 fetch, 최소 1회 이상")
        .isGreaterThanOrEqualTo(1L);
    assertThat(firstDelta - secondDelta)
        .as(
            "filter cache hit 으로 2nd 가 1st 대비 UserAccountEntity fetch 정확히 1회 감소해야 함. 1st=%d, 2nd=%d",
            firstDelta, secondDelta)
        .isEqualTo(1L);
  }

  // ============================================================
  // Scenario (2) — Block invalidation surfaces at next request
  // ============================================================

  @Test
  @DisplayName("BLOCKED 로 직접 save 후 GET /users/me — AFTER_COMMIT evict 로 즉시 403")
  void blockingAfterAuth_nextGetMeReturns403() {
    TestUser user = signupAndLogin("filter-cache-block");
    HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(user.accessToken()));

    // Prewarm — 1차 호출로 filter cache 를 ACTIVE 로 채운다.
    ResponseEntity<String> ok =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();

    // BLOCKED 로 save → UserAccountInvalidatedEvent → AFTER_COMMIT 이 cache evict.
    UserAccount loaded = loadUserAccountPort.findByUserId(user.userId()).orElseThrow();
    saveUserAccountPort.save(loaded.changeManagedStatus(AccountStatus.BLOCKED));

    ResponseEntity<String> blocked =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(blocked.getStatusCode())
        .as("cache 가 evict 안 됐으면 cached ACTIVE 로 200 — 403 이라야 invalidation 입증")
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ============================================================
  // Scenario (3) — Admin filter path: 2nd admin GET cache hit
  // ============================================================

  @Test
  @DisplayName("GET /admin/dashboard/user-stats 연속 호출 — 2번째는 filter 가 AdminAccountEntity fetch 0회")
  void authenticatedAdminGet_twice_secondCallFilterIssuesNoAdminAccountFetch() throws Exception {
    AdminCredential admin = seedAdmin();
    String adminToken = adminLogin(admin.loginId(), admin.plaintext());
    HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(adminToken));

    // adminLogin 의 save(updateLastLogin) 가 만든 fetch 는 본 검증 대상이 아님.
    statistics.clear();
    long beforeFirst = adminAccountFetchCount();

    ResponseEntity<String> first =
        restTemplate.exchange(
            baseUrl() + "/admin/dashboard/user-stats", HttpMethod.GET, request, String.class);
    assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
    long firstDelta = adminAccountFetchCount() - beforeFirst;

    long beforeSecond = adminAccountFetchCount();
    ResponseEntity<String> second =
        restTemplate.exchange(
            baseUrl() + "/admin/dashboard/user-stats", HttpMethod.GET, request, String.class);
    assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
    long secondDelta = adminAccountFetchCount() - beforeSecond;

    assertThat(firstDelta)
        .as("1st admin GET — filter 가 cache 채우기 위해 AdminAccountEntity 1회 fetch")
        .isEqualTo(1L);
    assertThat(secondDelta)
        .as(
            "2nd admin GET — filter 는 cache hit, fetch 0회여야 함. 1st=%d, 2nd=%d",
            firstDelta, secondDelta)
        .isZero();
  }

  // ============================================================
  // Measurement helpers
  // ============================================================

  /**
   * {@link UserAccountEntity} 가 DB 에서 읽혀온 횟수. {@link EntityStatistics#getLoadCount()} (일반
   * find/JPQL) 와 {@link EntityStatistics#getFetchCount()} (lazy initialization) 를 합산해 "이 entity 가
   * DB 에 한 번이라도 hit 했나" 를 측정한다. {@link AuthReissueCacheE2ETest#userAccountFetchCount()} 와 동일한 패턴.
   */
  private long userAccountFetchCount() {
    return entityFetchCount(UserAccountEntity.class);
  }

  private long adminAccountFetchCount() {
    return entityFetchCount(AdminAccountEntity.class);
  }

  private long entityFetchCount(Class<?> entityClass) {
    EntityStatistics es = statistics.getEntityStatistics(entityName(entityClass));
    return es.getLoadCount() + es.getFetchCount();
  }

  private String entityName(Class<?> entityClass) {
    String fqn = entityClass.getName();
    String simple = entityClass.getSimpleName();
    for (String name : statistics.getEntityNames()) {
      if (name.equals(fqn) || name.equals(simple) || name.endsWith("." + simple)) {
        return name;
      }
    }
    throw new IllegalStateException(
        simple
            + " not registered in Hibernate Statistics. Registered: "
            + java.util.Arrays.toString(statistics.getEntityNames()));
  }

  // ============================================================
  // Admin seed + login — mirrors AdminUserRoleManagementE2ETest 패턴
  // (POST /auth/signup 은 admin_accounts 행을 만들지 않으므로 JDBC 로 직접 시드한다)
  // ============================================================

  private AdminCredential seedAdmin() {
    String email =
        "filter-cache-admin-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
            + "@internal.mztk.local";
    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at)"
            + " VALUES (?, 'ADMIN_SEED', 'FilterCacheAdmin', NOW(), NOW())",
        email);
    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    String loginId = String.valueOf(10000000 + (int) (Math.random() * 90000000));
    String plaintext = "AdminP@ss" + UUID.randomUUID().toString().substring(0, 8);
    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        passwordEncoder.encode(plaintext));
    return new AdminCredential(userId, loginId, plaintext);
  }

  private String adminLogin(String loginId, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body =
        Map.of("provider", "LOCAL_ADMIN", "loginId", loginId, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
    return data.at("/accessToken").asText();
  }

  private record AdminCredential(Long userId, String loginId, String plaintext) {}
}
