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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * [MOM-464] {@code /auth/reissue} 엔드포인트의 실제 HTTP 경로 — POST /auth/reissue → {@code
 * ReissueTokenService.validateRefreshSubject} → {@code CheckAccountStatusUseCase.findStatus} — 가
 * MOM-464 에서 의도적으로 <b>cold path 로 DB 를 유지</b>함을 검증한다.
 *
 * <p>MOM-464 는 hot-path predicate(isActive/isDeleted/isBlocked)만 in-memory denylist 로 옮겼다. {@code
 * findStatus} 는 단순 boolean 이 아니라 정확한 상태값을 반환해야 하고, 무엇보다 user 가 존재하지 않는 경우({@code Optional.empty})와
 * ACTIVE 를 구분해야 하므로 denylist(absence=ACTIVE)로는 표현할 수 없다. 따라서 findStatus 는 uncached cold path 로 매 호출
 * DB 를 읽는다.
 *
 * <p>커버하는 contract:
 *
 * <ul>
 *   <li>(1) 연속 reissue 는 매 호출 UserAccount 를 1회 fetch 한다 — findStatus 는 캐시가 없는 cold path 이므로 hit 이
 *       없다.
 *   <li>(2) BLOCKED 로 명시적 save 후 reissue 가 403(USER_006)으로 거부된다 — findStatus 가 DB 를 직접 읽으므로
 *       invalidate 할 캐시 없이 즉시 관측된다.
 * </ul>
 *
 * <p>(과거 MOM-460 의 "login 의 save(updateLastLogin) 이 캐시를 invalidate 한다" 테스트는 findStatus 가 더 이상 캐시되지
 * 않으므로 (MOM-464 cold path) invalidate 할 대상이 사라져 삭제했다.)
 *
 * <p>측정: Hibernate Statistics 의 {@link UserAccountEntity} entity-scoped fetch count delta. reissue
 * 가 발사하는 다른 SQL (refresh_token UPDATE/INSERT 등) 은 다른 엔티티라 신호를 오염시키지 않으며, 글로벌 스케줄러의 JDBC 트래픽도 entity
 * scope 밖이라 영향이 없다.
 *
 * <p>denylist 는 공유 싱글턴 bean 이고 {@code DatabaseCleaner} 가 비우지 않으므로 매 테스트 전 비워 전원 ACTIVE 에서 시작한다 — 이
 * 리셋은 {@code E2ETestBase} 가 공통으로 처리한다 (MOM-464).
 */
@DisplayName("[E2E] /auth/reissue findStatus DB cold path (MOM-464)")
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
  @Autowired private JdbcTemplate jdbcTemplate;

  private Statistics statistics;

  @BeforeEach
  void enableAndResetStatistics() {
    // denylist 리셋은 E2ETestBase 가 매 테스트 전 공통으로 처리한다 (MOM-464).
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  /**
   * findStatus 가 uncached cold path 임을 입증 — 연속 reissue 가 매번 UserAccount 를 1회 fetch 한다.
   *
   * <p>MOM-464 에서 reissue 가 거치는 {@code CheckAccountStatusUseCase.findStatus} 는 의도적으로 캐시되지 않는다 (user
   * 부재 {@code Optional.empty} 와 ACTIVE 를 구분해야 하므로 denylist 로 표현 불가). 따라서 reissue1, reissue2 모두 DB 를
   * 1회씩 읽으며 — 캐시 hit 으로 인한 fetch 0회는 더 이상 발생하지 않는다.
   */
  @Test
  @DisplayName("/auth/reissue 연속 호출 — 매 호출 UserAccount fetch 1회 (findStatus cold path, 캐시 없음)")
  void reissue_alwaysReadsDb_noCacheOnColdPath() throws Exception {
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
        .as("1st reissue — findStatus 가 DB 를 1회 fetch (cold path)")
        .isEqualTo(1L);
    assertThat(secondReissueFetches)
        .as(
            "2nd reissue — 캐시가 없으므로 다시 DB 를 1회 fetch (cold path, hit 없음). 1st=%d, 2nd=%d",
            firstReissueFetches, secondReissueFetches)
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("/auth/reissue 후 계정 BLOCK write → findStatus 가 DB 직접 읽어 즉시 403 (캐시 invalidate 불필요)")
  void reissueAfterBlock_returns403_directDbRead() throws Exception {
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

    // findStatus 는 매 reissue 마다 DB 를 직접 읽으므로 invalidate 할 캐시 없이 BLOCKED 를 즉시 관측한다.
    ResponseEntity<String> reissueAfterBlock = reissue(refreshToken2);

    assertThat(reissueAfterBlock.getStatusCode())
        .as("findStatus 가 DB 를 직접 읽으므로 BLOCKED 가 즉시 반영 — 403 이어야 함")
        .isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = objectMapper.readTree(reissueAfterBlock.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("USER_006");
  }

  /**
   * UNVERIFIED 계정의 reissue 가 hot path 와 동일하게 거부됨을 검증 (리뷰 N3 회귀 가드).
   *
   * <p>리뷰 이전 {@code validateRefreshSubject} 는 {@code {DELETED, BLOCKED}} 만 거부하고 UNVERIFIED 는 통과시켜,
   * hot-path 필터(비-ACTIVE 전부 차단)와 불일치했다. 현재는 {@code AccountStatus} 전수 switch(default 없음)로 비-ACTIVE
   * 전부를 거부한다 — UNVERIFIED → {@code UserUnverifiedException} → {@code USER_007}(403).
   *
   * <p>{@code changeManagedStatus} 는 ACTIVE/BLOCKED 전이만 허용하므로(UNVERIFIED 거부) UNVERIFIED 상태는 JDBC 로
   * 직접 주입한다. reissue 의 {@code findStatus} 는 cold path 로 DB 를 직접 읽으므로 denylist 와 무관하게 이 상태를 관측한다.
   */
  @Test
  @DisplayName("/auth/reissue UNVERIFIED 계정 → findStatus 가 DB 읽어 403 (USER_007, hot path 와 정합)")
  void reissueByUnverifiedUser_returns403_userUnverified() throws Exception {
    String email = uniqueEmail();
    signup(email, DEFAULT_TEST_PASSWORD, "unverified-user");
    ResponseEntity<String> login = login(email, DEFAULT_TEST_PASSWORD);
    String refreshToken1 = extractRefreshToken(login);
    Long userId = extractUserId(login);

    ResponseEntity<String> reissue1 = reissue(refreshToken1);
    assertThat(reissue1.getStatusCode().is2xxSuccessful())
        .as("ACTIVE 상태에서의 첫 reissue 는 정상 — UNVERIFIED 전환 전 baseline")
        .isTrue();
    String refreshToken2 = extractRefreshToken(reissue1);

    // changeManagedStatus 는 UNVERIFIED 전이를 허용하지 않으므로 DB 에 직접 기록한다.
    int updated =
        jdbcTemplate.update(
            "UPDATE users_account SET status = 'UNVERIFIED' WHERE user_id = ?", userId);
    assertThat(updated).as("UNVERIFIED 로 전환된 계정이 정확히 1건이어야 함").isEqualTo(1);

    ResponseEntity<String> reissueAfterUnverify = reissue(refreshToken2);

    assertThat(reissueAfterUnverify.getStatusCode())
        .as("findStatus 가 DB 의 UNVERIFIED 를 읽어 reissue 를 거부 — 403 이어야 함 (hot path 와 정합)")
        .isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = objectMapper.readTree(reissueAfterUnverify.getBody());
    assertThat(body.at("/code").asText())
        .as("UNVERIFIED reissue 거부의 에러코드는 USER_007 이어야 함")
        .isEqualTo("USER_007");
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
