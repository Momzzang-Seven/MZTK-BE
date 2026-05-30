package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import org.hibernate.SessionFactory;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * [MOM-464 / 리뷰 W2] {@code account.status-registry.enabled=false} 일 때 인증 hot path 가 in-memory
 * denylist 가 아니라 <b>DB 로 직접 fallback</b> 함을 HTTP 레벨로 검증한다.
 *
 * <p>리뷰 지적(W2): denylist 가 비면 {@code absence=ACTIVE} 라 비-ACTIVE 사용자가 인증을 통과(fail-open)한다. 대응:
 * {@code enabled=false} 면 {@code CheckAccountStatusService} 가 denylist 를 읽지 않고 {@code
 * LoadUserAccountPort} 로 DB 를 직접 조회한다 — 즉 MOM-464 이전(per-request DB) 동작으로 안전하게 복귀한다. 이 클래스는 그
 * fallback 경로를 실제로 켜고(켜진 denylist 가 없는 채로) 두 가지를 입증한다:
 *
 * <ul>
 *   <li>(1) <b>DB 직격</b> — 인증된 {@code GET /users/me} 가 매 요청 {@link UserAccountEntity} 를 DB 에서 다시
 *       읽는다. {@code enabled=true} hot path 에서는 필터 기여분이 0 이라 요청당 fetch 가 1({@code
 *       AuthFilterCacheE2ETest} 시나리오 (1))인데, {@code enabled=false} 에서는 필터의 {@code isActive} DB 조회가
 *       더해져 요청당 fetch 가 <b>2 이상</b> 이고 매 요청 동일(캐시 없음)하다.
 *   <li>(2) <b>fail-closed</b> — denylist 가 비어 있어도 DB 가 BLOCKED 면 {@code GET /users/me} 가 403 으로
 *       거부된다. denylist 를 명시적으로 비운 뒤 호출하므로, 403 은 hot path 가 denylist(=비어있음→ACTIVE)가 아니라 DB 를 읽었다는
 *       직접 증거다.
 * </ul>
 *
 * <p>측정: {@code AuthFilterCacheE2ETest} 와 동일하게 Hibernate {@link Statistics} 의 {@link
 * UserAccountEntity} entity-scoped load/fetch count delta 를 쓴다. denylist 는 공유 싱글턴이라 {@code
 * E2ETestBase} 가 매 테스트 전 비운다.
 */
@DisplayName("[E2E] account.status-registry.enabled=false → 인증 hot path DB fallback (MOM-464 W2)")
@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "mztk.admin.bootstrap.enabled=false",
      // 핵심: registry 를 끈다 → warmup/reconcile 스케줄러 미생성, hot path 는 DB fallback.
      "account.status-registry.enabled=false"
    })
class AuthStatusRegistryDisabledE2ETest extends E2ETestBase {

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @Autowired private LoadUserAccountPort loadUserAccountPort;
  @Autowired private SaveUserAccountPort saveUserAccountPort;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private Statistics statistics;

  @BeforeEach
  void enableAndResetStatistics() {
    // denylist 리셋은 E2ETestBase 가 매 테스트 전 공통으로 처리한다 (MOM-464).
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  @Test
  @DisplayName("GET /users/me 연속 호출 — 필터가 매 요청 DB 를 다시 읽는다 (fetch ≥ 2, 캐시 없음)")
  void disabledRegistry_filterReadsDbOnEveryRequest() {
    TestUser user = signupAndLogin("disabled-registry-user");
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

    // enabled=false 면 필터의 isActive 가 LoadUserAccountPort 로 DB 를 읽는다(+1). /users/me 핸들러의 1회
    // fetch 와 합쳐 요청당 ≥ 2. enabled=true hot path(=denylist) 에서는 필터 기여분이 0 이라 요청당 1 이다 —
    // 이 ≥ 2 가 "denylist 가 아니라 DB 로 직접 쏜다" 의 직접 증거.
    assertThat(firstDelta)
        .as("enabled=false: 필터의 isActive 가 DB 를 읽어 요청당 fetch 가 핸들러 1 + 필터 ≥1 = ≥2 여야 함")
        .isGreaterThanOrEqualTo(2L);
    // 매 요청 동일 delta = 요청별 DB 재조회(캐시/메모리 hit 없음). 캐시가 끼면 2nd 가 작아진다.
    assertThat(secondDelta)
        .as("2nd 요청도 동일하게 DB 를 다시 읽어야 함(캐시 없음). 1st=%d, 2nd=%d", firstDelta, secondDelta)
        .isEqualTo(firstDelta);
  }

  @Test
  @DisplayName("denylist 가 비어도 DB 가 BLOCKED 면 GET /users/me 403 — DB fallback fail-closed")
  void disabledRegistry_blockedInDb_returns403_evenWithEmptyDenylist() {
    TestUser user = signupAndLogin("disabled-registry-block");
    HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(user.accessToken()));

    // 사전 호출 — ACTIVE 라 허용(2xx)됨이 baseline.
    ResponseEntity<String> ok =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(ok.getStatusCode().is2xxSuccessful())
        .as("block 직전 GET — 아직 ACTIVE 라 허용(2xx)됨이 baseline")
        .isTrue();

    // DB 를 BLOCKED 로 전이. (save 는 AFTER_COMMIT 이벤트로 denylist 에 put 도 하지만, enabled=false 에선
    // hot path 가 denylist 를 안 읽는다. 아래에서 denylist 를 명시적으로 비워 "denylist 는 비었는데 DB 는 BLOCKED"
    // 상태를 만든다 — 403 이면 hot path 가 denylist 가 아니라 DB 를 읽었다는 결정적 증거다.)
    UserAccount loaded = loadUserAccountPort.findByUserId(user.userId()).orElseThrow();
    saveUserAccountPort.save(loaded.changeManagedStatus(AccountStatus.BLOCKED));
    updateAccountStatusRegistryPort.replaceAll(java.util.Map::of);

    ResponseEntity<String> blocked =
        restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, request, String.class);
    assertThat(blocked.getStatusCode())
        .as(
            "denylist 가 비었으므로 denylist 를 읽었다면 200(ACTIVE) — 403 이라야 DB fallback 이 BLOCKED 를 차단함을 입증")
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ============================================================
  // Measurement helpers (AuthFilterCacheE2ETest 와 동일 패턴)
  // ============================================================

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
}
