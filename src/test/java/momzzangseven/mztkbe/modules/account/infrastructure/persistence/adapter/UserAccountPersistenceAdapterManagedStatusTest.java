package momzzangseven.mztkbe.modules.account.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository.UserAccountJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("UserAccountPersistenceAdapter managed-status DataJpaTest")
class UserAccountPersistenceAdapterManagedStatusTest {

  @Autowired private TestEntityManager em;

  @Autowired private UserAccountJpaRepository userAccountJpaRepository;

  @Test
  @DisplayName("status filter 와 userIds filter 를 함께 지원한다")
  void load_supportsStatusAndUserIdsFilter() {
    ApplicationEventPublisher noOpPublisher = event -> {};
    UserAccountPersistenceAdapter adapter =
        new UserAccountPersistenceAdapter(userAccountJpaRepository, noOpPublisher);

    persistAccount(10L, AccountStatus.ACTIVE);
    persistAccount(11L, AccountStatus.BLOCKED);
    persistAccount(12L, AccountStatus.BLOCKED);

    Map<Long, AccountStatus> blocked = adapter.load(null, AccountStatus.BLOCKED);
    Map<Long, AccountStatus> selected = adapter.load(List.of(10L, 11L), null);
    Map<Long, AccountStatus> selectedBlocked =
        adapter.load(List.of(10L, 11L), AccountStatus.BLOCKED);

    assertThat(blocked).containsOnlyKeys(11L, 12L);
    assertThat(selected)
        .containsEntry(10L, AccountStatus.ACTIVE)
        .containsEntry(11L, AccountStatus.BLOCKED);
    assertThat(selectedBlocked).containsOnlyKeys(11L);
  }

  @Test
  @DisplayName("loadAllNonActive 는 ACTIVE 가 아닌 모든 계정을 userId→status 로 반환한다")
  void loadAllNonActive_returnsEveryNonActiveAccount() {
    ApplicationEventPublisher noOpPublisher = event -> {};
    UserAccountPersistenceAdapter adapter =
        new UserAccountPersistenceAdapter(userAccountJpaRepository, noOpPublisher);

    persistAccount(20L, AccountStatus.ACTIVE);
    persistAccount(21L, AccountStatus.BLOCKED);
    persistAccount(22L, AccountStatus.DELETED);
    persistAccount(23L, AccountStatus.UNVERIFIED);

    Map<Long, AccountStatus> nonActive = adapter.loadAllNonActive();

    assertThat(nonActive)
        .hasSize(3)
        .containsEntry(21L, AccountStatus.BLOCKED)
        .containsEntry(22L, AccountStatus.DELETED)
        .containsEntry(23L, AccountStatus.UNVERIFIED)
        .doesNotContainKey(20L);
  }

  @Test
  @DisplayName("loadAllNonActive 는 비활성 계정이 없으면 빈 맵을 반환한다")
  void loadAllNonActive_returnsEmptyMapWhenNone() {
    ApplicationEventPublisher noOpPublisher = event -> {};
    UserAccountPersistenceAdapter adapter =
        new UserAccountPersistenceAdapter(userAccountJpaRepository, noOpPublisher);

    persistAccount(30L, AccountStatus.ACTIVE);
    persistAccount(31L, AccountStatus.ACTIVE);

    assertThat(adapter.loadAllNonActive()).isEmpty();
  }

  private void persistAccount(Long userId, AccountStatus status) {
    em.persist(
        UserAccountEntity.builder()
            .userId(userId)
            .provider(AuthProvider.LOCAL)
            .status(status)
            .build());
    em.flush();
  }
}
