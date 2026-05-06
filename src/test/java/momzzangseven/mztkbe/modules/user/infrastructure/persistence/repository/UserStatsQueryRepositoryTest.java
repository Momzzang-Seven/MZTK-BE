package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("UserStatsQueryRepository DataJpaTest")
class UserStatsQueryRepositoryTest {

  @Autowired private UserStatsQueryRepository userStatsQueryRepository;

  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("USER/TRAINER 계정만 total, active, blocked 통계에 집계한다")
  void countUserAccounts_filtersAdminRoles() {
    persistUserAccount("user@test.com", UserRole.USER, AccountStatus.ACTIVE);
    persistUserAccount("trainer@test.com", UserRole.TRAINER, AccountStatus.BLOCKED);
    persistUserAccount("admin@test.com", UserRole.ADMIN, AccountStatus.ACTIVE);
    persistUserAccount("seed@test.com", UserRole.ADMIN_SEED, AccountStatus.BLOCKED);
    persistUserAccount("generated@test.com", UserRole.ADMIN_GENERATED, AccountStatus.ACTIVE);
    em.flush();

    List<UserRole> countedRoles = List.of(UserRole.USER, UserRole.TRAINER);

    assertThat(userStatsQueryRepository.countUserAccountsByRoles(countedRoles)).isEqualTo(2L);
    assertThat(
            userStatsQueryRepository.countUserAccountsByStatusAndRoles(
                AccountStatus.ACTIVE, countedRoles))
        .isEqualTo(1L);
    assertThat(
            userStatsQueryRepository.countUserAccountsByStatusAndRoles(
                AccountStatus.BLOCKED, countedRoles))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("roleCounts 도 USER/TRAINER 계정만 집계한다")
  void countUsersByRoles_filtersRequestedRoles() {
    persistUserAccount("user@test.com", UserRole.USER, AccountStatus.ACTIVE);
    persistUserAccount("trainer@test.com", UserRole.TRAINER, AccountStatus.BLOCKED);
    persistUserAccount("admin@test.com", UserRole.ADMIN_GENERATED, AccountStatus.ACTIVE);
    em.flush();

    Map<UserRole, Long> counts =
        userStatsQueryRepository
            .countUsersByRoles(List.of(UserRole.USER, UserRole.TRAINER))
            .stream()
            .collect(Collectors.toMap(UserStatsProjection::role, UserStatsProjection::count));

    assertThat(counts).containsEntry(UserRole.USER, 1L).containsEntry(UserRole.TRAINER, 1L);
    assertThat(counts).doesNotContainKey(UserRole.ADMIN_GENERATED);
  }

  private void persistUserAccount(String email, UserRole role, AccountStatus status) {
    UserEntity user =
        em.persist(
            UserEntity.builder().email(email).nickname(email.substring(0, 4)).role(role).build());
    Instant now = Instant.now();
    em.persist(
        UserAccountEntity.builder()
            .userId(user.getId())
            .provider(AuthProvider.LOCAL)
            .passwordHash("{noop}password")
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }
}
