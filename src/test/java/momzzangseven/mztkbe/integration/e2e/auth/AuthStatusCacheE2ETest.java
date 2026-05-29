package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.UpdateAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * [MOM-464] 인증 경로 denylist 효과 검증 — 실제 PostgreSQL 대상으로 Hibernate Statistics 로 JDBC prepared
 * statement 수를 직접 측정해 hot-path predicate({@code isActive/isDeleted/isBlocked})가 in-memory denylist
 * 만으로 서빙되어 ZERO JDBC 임을 확인한다.
 *
 * <p>MOM-460 의 Caffeine 캐시(temporal locality 부재로 hit≈0)를 걷어내고, 비-ACTIVE 사용자만 추적하는 in-memory
 * denylist(absence=ACTIVE)로 교체했다. ACTIVE 사용자는 denylist 에 없으므로 {@code statusOf} 가 in-memory 로 ACTIVE
 * 를 반환하고 DB 를 전혀 치지 않는다. 상태 전이는 {@code UserAccountStatusChangedEvent} 를 통해 AFTER_COMMIT 에 denylist
 * 로 전파된다. phase2 BP 의 풀 점유 감소 가설이 코드 레벨에서 성립함을 보여주는 회귀 가드.
 *
 * <p>REST signup/login 은 회피 — HTTP 경로는 본 검증 대상이 아니고, 자체적으로 다수의 SQL 을 발사해 측정 신호를 흐림. 대신 {@link
 * UserJpaRepository} + {@link SaveUserAccountPort} 로 entity 를 직접 시드한다.
 *
 * <p>denylist 는 공유 싱글턴 bean — {@code DatabaseCleaner} 는 DB 만 비우고 in-memory denylist 는 건드리지 않으므로 매
 * 테스트 전 {@link UpdateAccountStatusRegistryPort#replaceAll(java.util.Map)} 로 비워 전원 ACTIVE 상태에서 시작한다.
 */
@DisplayName("[E2E] Auth status denylist 효과 검증 (MOM-464)")
@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "mztk.admin.bootstrap.enabled=false"
    })
class AuthStatusCacheE2ETest extends E2ETestBase {

  @Autowired private CheckAccountStatusUseCase checkAccountStatusUseCase;
  @Autowired private LoadUserAccountPort loadUserAccountPort;
  @Autowired private SaveUserAccountPort saveUserAccountPort;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private UpdateAccountStatusRegistryPort updateAccountStatusRegistryPort;

  private Statistics statistics;

  @BeforeEach
  void resetDenylistAndStatistics() {
    // denylist 는 공유 싱글턴 — DatabaseCleaner 가 비우지 않으므로 매 테스트 전 명시적으로 비운다 (전원 ACTIVE).
    updateAccountStatusRegistryPort.replaceAll(java.util.Map.of());
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  @Test
  @DisplayName("isActive 는 매 호출 JDBC prepared statement 미발행 (denylist hot path, ZERO JDBC)")
  void isActive_issuesNoJdbc_denylistHotPath() {
    Long userId = seedActiveUser();
    // 시드한 사용자는 ACTIVE → denylist 에 부재 → statusOf 가 in-memory 로 ACTIVE 반환. prime 불필요.
    long baseline = statistics.getPrepareStatementCount();

    boolean first = checkAccountStatusUseCase.isActive(userId);
    long afterFirst = statistics.getPrepareStatementCount();

    boolean second = checkAccountStatusUseCase.isActive(userId);
    long afterSecond = statistics.getPrepareStatementCount();

    assertThat(first).isTrue();
    assertThat(second).isTrue();
    // denylist 는 첫 호출부터 in-memory 조회만 — 캐시 워밍업 같은 첫 miss 가 없다.
    assertThat(afterFirst - baseline).isZero();
    assertThat(afterSecond - afterFirst).isZero();
  }

  @Test
  @DisplayName("isActive → isDeleted → isBlocked 연속 호출은 denylist 조회만 — SQL 0회")
  void predicateCalls_issueNoSql() {
    Long userId = seedActiveUser();
    long baseline = statistics.getPrepareStatementCount();

    boolean active = checkAccountStatusUseCase.isActive(userId);
    long afterActive = statistics.getPrepareStatementCount();

    boolean deleted = checkAccountStatusUseCase.isDeleted(userId);
    boolean blocked = checkAccountStatusUseCase.isBlocked(userId);
    long afterAll = statistics.getPrepareStatementCount();

    assertThat(active).isTrue();
    assertThat(deleted).isFalse();
    assertThat(blocked).isFalse();
    // 모든 predicate 가 in-memory denylist 조회 — 어떤 호출도 SQL 을 발사하지 않는다.
    assertThat(afterActive - baseline).isZero();
    assertThat(afterAll - afterActive).isZero();
  }

  @Test
  @DisplayName(
      "BLOCKED save → AFTER_COMMIT 이벤트가 denylist 에 전파 — 다음 predicate 는 추가 SQL 0회로 BLOCKED 관측")
  void statusChange_propagatesToDenylist_viaAfterCommitEvent_noExtraSql() {
    Long userId = seedActiveUser();

    long baseline = statistics.getPrepareStatementCount();
    boolean active = checkAccountStatusUseCase.isActive(userId);
    long afterActive = statistics.getPrepareStatementCount();
    // ACTIVE 사용자는 denylist 에 부재 → in-memory ACTIVE, SQL 0회.
    assertThat(active).isTrue();
    assertThat(afterActive - baseline).isZero();

    UserAccount loaded = loadUserAccountPort.findByUserId(userId).orElseThrow();
    // BLOCKED 로 save → UserAccountStatusChangedEvent(BLOCKED) → AFTER_COMMIT 핸들러가
    // denylist 에 BLOCKED 를 put (동기, save() 반환 전 완료).
    saveUserAccountPort.save(loaded.changeManagedStatus(AccountStatus.BLOCKED));
    long afterSave = statistics.getPrepareStatementCount();

    boolean stillActive = checkAccountStatusUseCase.isActive(userId);
    boolean nowBlocked = checkAccountStatusUseCase.isBlocked(userId);
    long afterPredicates = statistics.getPrepareStatementCount();

    // 상태 전이가 denylist 에 반영됐음을 행동으로 입증.
    assertThat(stillActive).isFalse();
    assertThat(nowBlocked).isTrue();
    // denylist 조회만 — save 이후 predicate 들은 DB 를 다시 읽지 않는다 (추가 SQL 0회).
    assertThat(afterPredicates - afterSave).isZero();
  }

  private Long seedActiveUser() {
    String email = "cache-e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    UserEntity user =
        userJpaRepository.save(
            UserEntity.builder().email(email).role(UserRole.USER).nickname("cache-e2e").build());
    saveUserAccountPort.save(UserAccount.createLocal(user.getId(), "$2a$10$placeholderHashValue"));
    return user.getId();
  }
}
