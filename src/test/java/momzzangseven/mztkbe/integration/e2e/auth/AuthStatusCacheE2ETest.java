package momzzangseven.mztkbe.integration.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
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
 * [MOM-460] 인증 경로 Caffeine 캐시 효과 검증 — 실제 PostgreSQL 대상으로 Hibernate Statistics 로 JDBC prepared
 * statement 수를 직접 측정해 캐시가 SQL 을 줄이는지 확인한다.
 *
 * <p>단위 테스트({@code CheckAccountStatusServiceTest})는 mock 포트 호출 횟수만 검증하지만, E2E 는 **실제 JPA → JDBC**
 * 까지 내려가는 경로에서 캐시 효과가 SQL 절감으로 이어지는지 확인한다. phase2 BP 의 풀 점유 감소 가설이 코드 레벨에서 성립함을 보여주는 회귀 가드.
 *
 * <p>REST signup/login 은 회피 — HTTP 경로는 본 PR 의 검증 대상이 아니고, 자체적으로 다수의 SQL 을 발사해 측정 신호를 흐림. 대신 {@link
 * UserJpaRepository} + {@link SaveUserAccountPort} 로 entity 를 직접 시드한다.
 */
@DisplayName("[E2E] Auth status cache 효과 검증 (MOM-460)")
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

  private Statistics statistics;

  @BeforeEach
  void enableStatistics() {
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  @Test
  @DisplayName("isActive 연속 호출은 두 번째부터 JDBC prepared statement 미발행 (cache hit)")
  void repeatedIsActive_secondCallIssuesNoJdbcStatement() {
    Long userId = seedActiveUser();
    primeCache(userId);
    long baseline = statistics.getPrepareStatementCount();

    boolean first = checkAccountStatusUseCase.isActive(userId);
    long afterFirst = statistics.getPrepareStatementCount();

    boolean second = checkAccountStatusUseCase.isActive(userId);
    long afterSecond = statistics.getPrepareStatementCount();

    assertThat(first).isTrue();
    assertThat(second).isTrue();
    assertThat(afterFirst - baseline).isZero();
    assertThat(afterSecond - afterFirst).isZero();
  }

  @Test
  @DisplayName("isActive → isDeleted → isBlocked 연속 호출은 동일 userId 면 SQL 1회만 발행")
  void crossMethodCalls_shareSingleQuery() {
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
    assertThat(afterActive - baseline).isEqualTo(1L);
    assertThat(afterAll - afterActive).isZero();
  }

  @Test
  @DisplayName("UserAccount save 후 AFTER_COMMIT 이벤트가 캐시 항목을 evict — 다음 isActive 는 새 SQL")
  void saveTriggersAfterCommitEviction() {
    Long userId = seedActiveUser();

    checkAccountStatusUseCase.isActive(userId);
    long afterFirstMiss = statistics.getPrepareStatementCount();

    checkAccountStatusUseCase.isActive(userId);
    long afterCachedHit = statistics.getPrepareStatementCount();
    assertThat(afterCachedHit - afterFirstMiss).isZero();

    UserAccount loaded = loadUserAccountPort.findByUserId(userId).orElseThrow();
    saveUserAccountPort.save(loaded.changeManagedStatus(AccountStatus.BLOCKED));
    long afterSave = statistics.getPrepareStatementCount();

    boolean stillActive = checkAccountStatusUseCase.isActive(userId);
    boolean nowBlocked = checkAccountStatusUseCase.isBlocked(userId);
    long afterPostInvalidation = statistics.getPrepareStatementCount();

    assertThat(stillActive).isFalse();
    assertThat(nowBlocked).isTrue();
    assertThat(afterPostInvalidation - afterSave).isEqualTo(1L);
  }

  private Long seedActiveUser() {
    String email = "cache-e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    UserEntity user =
        userJpaRepository.save(
            UserEntity.builder().email(email).role(UserRole.USER).nickname("cache-e2e").build());
    saveUserAccountPort.save(UserAccount.createLocal(user.getId(), "$2a$10$placeholderHashValue"));
    return user.getId();
  }

  /**
   * Forces the first DB read so subsequent assertions measure cache hits only — saving the account
   * inserts the row but does not populate the cache (no isActive call), so we explicitly fill it
   * once and reset the counter from there.
   */
  private void primeCache(Long userId) {
    checkAccountStatusUseCase.isActive(userId);
    statistics.clear();
  }
}
