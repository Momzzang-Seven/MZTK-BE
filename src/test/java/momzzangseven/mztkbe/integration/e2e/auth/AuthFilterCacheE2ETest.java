package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 * [MOM-464] JwtAuthenticationFilter 의 hot path — {@code checkAccountStatusUseCase.isActive /
 * isDeleted / isBlocked} — 가 in-memory denylist 로 서빙되어 인증된 매 요청마다 filter 가 DB(UserAccountEntity
 * fetch)를 치지 않음을 HTTP 레벨로 검증한다. admin 측({@code checkAdminAccountStatusUseCase.isActiveAdmin})은
 * MOM-464 범위 밖이라 여전히 Caffeine cache 를 사용한다 (시나리오 (3) 유지).
 *
 * <p>{@code AuthStatusCacheE2ETest} 는 use case 를 직접 호출해 denylist 동작만 측정했고, {@code
 * AuthReissueCacheE2ETest} 는 {@code /auth/reissue} (filter 가 access token 부재로 early bail out 하는 경로,
 * findStatus cold path) 를 검증했다. 본 테스트는 <b>인증된 일반 endpoint 요청</b> — 즉 prod 트래픽의 절대다수 형태 — 의 filter
 * 단계가 denylist 로 흡수되는지를 입증한다.
 *
 * <p>커버하는 contract:
 *
 * <ul>
 *   <li>(1) 같은 access token 으로 {@code GET /users/me} 연속 호출 시 filter 가 발사하는 {@link
 *       UserAccountEntity} fetch 가 <b>매 요청 0 회</b> — first/second delta 가 동일 (filter 는 어느 요청에서도
 *       fetch 를 더하지 않음).
 *   <li>(2) {@link AccountStatus#BLOCKED} 로 직접 save → {@code
 *       UserAccountStatusChangedEvent(BLOCKED)} → AFTER_COMMIT 이 denylist 에 put → 다음 {@code GET
 *       /users/me} 즉시 403 (stale 방어).
 *   <li>(2-evict) BLOCKED 계정을 다시 {@link AccountStatus#ACTIVE} 로 save (admin 차단 해제 경로 {@code
 *       changeManagedStatus(ACTIVE)}) → {@code UserAccountStatusChangedEvent(ACTIVE)} →
 *       AFTER_COMMIT 이 denylist 에서 evict → <b>동일 토큰</b>으로 다음 {@code GET /users/me} 200 (재허용). put
 *       방향(차단)뿐 아니라 evict 방향(재허용)도 hot path 에 즉시 반영됨을 입증.
 *   <li>(3) admin token 으로 {@code GET /admin/dashboard/user-stats} 연속 호출 시 두 번째는 filter 가 발사하는
 *       {@link AdminAccountEntity} fetch 가 0 회 — admin 측은 MOM-464 범위 밖이라 여전히 Caffeine cache
 *       contract 가 성립함을 확인 (이 시나리오는 변경하지 않는다).
 *   <li>(4) 같은 access token 으로 {@code GET /users/me} 연속 호출 시 filter 가 발사하는 {@code
 *       hikaricp.connections.acquire} count 가 <b>매 요청 동일</b> — denylist hot path 는 어느 요청에서도
 *       HikariCP connection 을 0 회 잡는다 (pool acquire counter 로 직접 측정).
 * </ul>
 *
 * <p>측정 원리: Hibernate Statistics 의 entity-scoped load/fetch count delta.
 *
 * <ul>
 *   <li>{@code /users/me} 의 핸들러 {@code GetMyProfileService} 는 {@code GetAuthProviderService} 경유로
 *       {@link UserAccountEntity} 를 매 요청 동일하게 1 회 fetch 한다 (denylist 미경유 핸들러 경로). filter 는 denylist
 *       in-memory 조회라 fetch 0 회 — 따라서 first 와 second 의 fetch delta 가 <b>동일</b>해야 한다 (filter 의
 *       miss/hit 비대칭이 사라짐 = filter 기여분 0).
 *   <li>{@code /admin/dashboard/user-stats} 의 {@code GetAdminUserStatsService} 는 통계 집계 쿼리만 발사하고
 *       {@link AdminAccountEntity} 를 추가로 fetch 하지 않으며, {@code @AdminOnly} 부수 효과 (audit write) 도 별
 *       entity 라 측정 신호 밖. admin 측은 여전히 Caffeine 이므로 절대값 1/0 assertion 이 그대로 성립.
 * </ul>
 *
 * <p>denylist 는 공유 싱글턴 bean 이고 {@code DatabaseCleaner} 가 비우지 않으므로 매 테스트 전 비워 전원 ACTIVE 에서 시작한다 — 이
 * 리셋은 {@code E2ETestBase} 가 공통으로 처리한다 (MOM-464).
 */
@DisplayName("[E2E] JwtAuthenticationFilter denylist hot path + invalidation (MOM-464)")
@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      // AdminUserRoleManagementE2ETest 와 동일한 admin 설정 — admin 시드/로그인 흐름이 정상 동작하도록.
      // mztk.admin.bootstrap.enabled=false 로 두면 LOCAL_ADMIN 로그인이 AUTH_002 로 실패한다.
      "mztk.admin.recovery.anchor=test-e2e-recovery-anchor",
      "mztk.admin.seed.seed-count=2",
      // 시나리오 (4) 는 JVM-global Hikari acquire counter 를 두 요청 사이 delta 로 측정한다.
      // integration 프로파일에서 fixedDelay 로 도는 백그라운드 스케줄러가 그 사이에 connection 을
      // 잡으면 counter 가 요청 스레드 밖에서 +1 되어 시나리오 (4) 가 플래키해진다. 이 테스트 클래스는
      // 어떤 스케줄러도 필요로 하지 않으므로 (denylist 리셋은 E2ETestBase 가 처리), pool 을 건드리는
      // 근거리 스케줄러를 전부 비활성화한다.
      //   - TransactionIssuerWorker (@Scheduled fixedDelay 1s, web3.reward-token.enabled 게이트)
      "web3.reward-token.enabled=false",
      //   - AccountStatusRegistryReconciliationScheduler (5분 cron, 이 플래그는 reconcile/warmup
      //     스케줄러만 게이트하고 denylist 이벤트 핸들러는 게이트하지 않으므로 시나리오 (2)/(2-evict) 안전)
      "account.status-registry.enabled=false",
      //   - ExternalDisconnectRetryScheduler (@Scheduled fixedDelay, 무조건) 의 발사를 테스트 윈도우
      //     밖으로 밀어낸다.
      "withdrawal.external-disconnect.fixed-delay=86400000"
    })
class AuthFilterCacheE2ETest extends E2ETestBase {

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @Autowired private LoadUserAccountPort loadUserAccountPort;
  @Autowired private SaveUserAccountPort saveUserAccountPort;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MeterRegistry meterRegistry;

  private Statistics statistics;

  @BeforeEach
  void enableAndResetStatistics() {
    // denylist 리셋은 E2ETestBase 가 매 테스트 전 공통으로 처리한다 (MOM-464).
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  // ============================================================
  // Scenario (1) — denylist hot path (filter 0 fetch on every request)
  // ============================================================

  @Test
  @DisplayName("GET /users/me 연속 호출 — filter 는 매 요청 UserAccountEntity fetch 0회 (denylist hot path)")
  void authenticatedGetMe_filterIssuesNoUserAccountFetch_onEveryRequest() {
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

    // /users/me 핸들러는 UserAccountEntity 를 매 요청 정확히 1회 fetch (GetAuthProviderService 경유).
    // filter 는 in-memory denylist 조회라 어느 요청에서도 fetch 를 0회 추가한다.
    // 따라서 핸들러의 단일 fetch 만 남아 매 요청 delta == 1 이어야 한다 — filter 가 회귀로 DB read 를
    // 재도입하면 delta 가 2 가 되어 (여전히 동일하더라도) 절대값 assertion 이 이를 잡아낸다.
    assertThat(firstDelta)
        .as(
            "denylist hot path: filter fetches 0 UserAccountEntity; only /users/me 핸들러의 1회 fetch 남음")
        .isEqualTo(1L);
    assertThat(secondDelta)
        .as("2nd 요청도 동일 — 필터는 매 요청 0 fetch. 1st=%d, 2nd=%d", firstDelta, secondDelta)
        .isEqualTo(1L);
  }

  // ============================================================
  // Scenario (2) — Block invalidation surfaces at next request
  // ============================================================

  @Test
  @DisplayName("BLOCKED 로 직접 save 후 GET /users/me — AFTER_COMMIT denylist put 으로 즉시 403")
  void blockingAfterAuth_nextGetMeReturns403() {
    TestUser user = signupAndLogin("filter-cache-block");
    HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(user.accessToken()));

    // 사전 호출 — denylist 는 비어있어(전원 ACTIVE) 200 을 받는다. block 전 "허용됐었다" baseline.
    ResponseEntity<String> ok =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(ok.getStatusCode().is2xxSuccessful())
        .as("block 직전 prewarm GET — 아직 ACTIVE 라 허용(2xx)됨이 baseline")
        .isTrue();

    // BLOCKED 로 save → UserAccountStatusChangedEvent(BLOCKED) → AFTER_COMMIT 핸들러가 denylist 에 put.
    UserAccount loaded = loadUserAccountPort.findByUserId(user.userId()).orElseThrow();
    saveUserAccountPort.save(loaded.changeManagedStatus(AccountStatus.BLOCKED));

    ResponseEntity<String> blocked =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(blocked.getStatusCode())
        .as("denylist 에 BLOCKED 가 전파 안 됐으면 statusOf=ACTIVE 로 200 — 403 이라야 전파 입증")
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ============================================================
  // Scenario (2-evict) — Reactivation evicts denylist, surfaces at next request
  // ============================================================

  @Test
  @DisplayName("BLOCKED 후 ACTIVE 로 복구 → AFTER_COMMIT denylist evict → 동일 토큰 GET /users/me 200")
  void reactivationEvictsDenylist_nextGetMeReturns200() {
    TestUser user = signupAndLogin("filter-cache-reactivate");
    // BLOCK 과 REACTIVATE 사이에 재로그인하지 않는다 — 발급 시점에 받은 access token 을 TTL 동안 그대로 재사용한다.
    // 핵심은 denylist evict 가 인증 hot path 에 즉시 반영되는지이지, 토큰 재발급이 아니다 (BLOCKED 계정은 reissue 불가).
    HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(user.accessToken()));

    // (1) BLOCKED 로 save → UserAccountStatusChangedEvent(BLOCKED) → AFTER_COMMIT denylist put →
    // 403.
    UserAccount active = loadUserAccountPort.findByUserId(user.userId()).orElseThrow();
    saveUserAccountPort.save(active.changeManagedStatus(AccountStatus.BLOCKED));

    ResponseEntity<String> whileBlocked =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(whileBlocked.getStatusCode())
        .as("BLOCKED 전파됐으면 denylist put 으로 403 — evict 검증의 baseline")
        .isEqualTo(HttpStatus.FORBIDDEN);

    // (2) ACTIVE 로 복구 → BLOCKED→ACTIVE 는 changeManagedStatus 의 valid managed transition →
    //     UserAccountStatusChangedEvent(ACTIVE) → AFTER_COMMIT 핸들러가 denylist 에서 evict.
    UserAccount blocked = loadUserAccountPort.findByUserId(user.userId()).orElseThrow();
    saveUserAccountPort.save(blocked.changeManagedStatus(AccountStatus.ACTIVE));

    // (3) 동일 토큰으로 다시 호출 — evict 가 hot path 에 반영됐으면 statusOf=ACTIVE 로 200.
    ResponseEntity<String> afterReactivate =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(afterReactivate.getStatusCode())
        .as("ACTIVE 복구가 denylist 에서 evict 안 됐으면 stale BLOCKED 로 여전히 403 — 200 이라야 evict 전파 입증")
        .isEqualTo(HttpStatus.OK);
  }

  // ============================================================
  // Scenario (3) — Admin filter path: 2nd admin GET cache hit
  // ============================================================

  @Test
  @DisplayName("GET /admin/dashboard/user-stats 연속 호출 — 2번째는 filter 가 AdminAccountEntity fetch 0회")
  void authenticatedAdminGet_twice_secondCallFilterIssuesNoAdminAccountFetch() throws Exception {
    // 이 시나리오는 admin 측이 여전히 Caffeine cache 임을 검증 — admin status 캐싱은 의도적으로 MOM-464 범위 밖.
    // 매 테스트 truncate 후에도 DB ID 는 monotonic 으로 재사용되지 않아, 새 admin user ID 가 cache key
    // 충돌을 일으키지 않으므로 (DatabaseCleaner 가 Caffeine 을 비우지 않아도) 안전하다.
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
  // Scenario (4) — Direct measurement of HikariCP acquire delta
  //   "denylist hot path 에서 filter 는 매 요청 connection grab = 0" 을
  //   entity fetch 가 아닌 pool acquire counter 자체로 직접 입증.
  // ============================================================

  @Test
  @DisplayName("GET /users/me 연속 호출 — filter 는 매 요청 Hikari acquire 0회 (first/second delta 동일)")
  void filterIssuesZeroHikariAcquire_onEveryRequest() {
    TestUser user = signupAndLogin("filter-grab-measurement");
    HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(user.accessToken()));

    // Hikari Micrometer Timer 가 첫 acquire 시점에 등록되므로 — signup/login 으로 이미 등록됨을 보장.
    long beforeFirst = hikariAcquireCount();
    ResponseEntity<String> first =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
    long firstDelta = hikariAcquireCount() - beforeFirst;

    long beforeSecond = hikariAcquireCount();
    ResponseEntity<String> second =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
    long secondDelta = hikariAcquireCount() - beforeSecond;

    // 핸들러는 양 요청에서 동일한 acquire 수를 발생 (GetMyProfileService 의 read tx 들) → 양변 상쇄.
    // filter 는 denylist in-memory 조회라 어느 요청에서도 connection 을 0회 잡는다 (loader/tx 없음).
    // → first/second acquire delta 가 동일. "denylist hot path 에서 filter 는 매 요청 connection grab = 0"
    //   의 직접 측정 — miss/hit 비대칭(과거 차이=1)이 사라졌다.
    assertThat(firstDelta)
        .as("1st GET /users/me — 핸들러 acquire, 최소 1회 이상")
        .isGreaterThanOrEqualTo(1L);
    assertThat(firstDelta)
        .as(
            "denylist hot path: filter 는 매 요청 acquire 0회 → first/second delta 동일. 1st=%d, 2nd=%d",
            firstDelta, secondDelta)
        .isEqualTo(secondDelta);
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

  /**
   * Spring Boot 가 자동 등록하는 {@code hikaricp.connections.acquire} {@link Timer} 의 count — 즉 HikariCP
   * 에서 connection 을 가져오려 시도한 횟수 (성공 + 실패). denylist hot path 에서 필터가 0 acquire 함을 측정하는 데 사용한다.
   */
  private long hikariAcquireCount() {
    Timer timer = meterRegistry.find("hikaricp.connections.acquire").timer();
    if (timer == null) {
      throw new IllegalStateException(
          "hikaricp.connections.acquire metric not registered — Hikari Micrometer binding 확인 필요");
    }
    return timer.count();
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
