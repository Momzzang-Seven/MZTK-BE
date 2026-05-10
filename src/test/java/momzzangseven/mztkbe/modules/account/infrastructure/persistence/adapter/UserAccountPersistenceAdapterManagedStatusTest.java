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
    UserAccountPersistenceAdapter adapter =
        new UserAccountPersistenceAdapter(userAccountJpaRepository);

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
