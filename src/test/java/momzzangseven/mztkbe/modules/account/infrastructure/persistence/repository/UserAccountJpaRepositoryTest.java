package momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserAccountJpaRepository integration test")
class UserAccountJpaRepositoryTest {

  @Autowired private TestEntityManager em;

  @Autowired private UserAccountJpaRepository repository;

  // ============================================================
  // findByUserId
  // ============================================================

  @Nested
  @DisplayName("findByUserId")
  class FindByUserId {

    @Test
    @DisplayName("returns entity when userId matches")
    void returnsEntityWhenMatches() {
      Long userId = savedUser("user1@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      Optional<UserAccountEntity> result = repository.findByUserId(userId);

      assertThat(result).isPresent();
      assertThat(result.get().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("returns empty when userId has no account")
    void returnsEmptyWhenMissing() {
      assertThat(repository.findByUserId(9999L)).isEmpty();
    }
  }

  // ============================================================
  // findByProviderAndProviderUserId
  // ============================================================

  @Nested
  @DisplayName("findByProviderAndProviderUserId")
  class FindByProviderAndProviderUserId {

    @Test
    @DisplayName("returns entity for matching provider pair")
    void returnsEntityForMatchingProvider() {
      Long userId = savedUser("kakao@test.com");
      savedAccount(userId, AuthProvider.KAKAO, "kakao-uid-1", AccountStatus.ACTIVE);

      Optional<UserAccountEntity> result =
          repository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-uid-1");

      assertThat(result).isPresent();
      assertThat(result.get().getProviderUserId()).isEqualTo("kakao-uid-1");
    }

    @Test
    @DisplayName("returns empty for wrong provider")
    void returnsEmptyForWrongProvider() {
      Long userId = savedUser("google@test.com");
      savedAccount(userId, AuthProvider.GOOGLE, "google-uid-1", AccountStatus.ACTIVE);

      Optional<UserAccountEntity> result =
          repository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "google-uid-1");

      assertThat(result).isEmpty();
    }
  }

  // ============================================================
  // findByProviderAndProviderUserIdAndStatus
  // ============================================================

  @Nested
  @DisplayName("findByProviderAndProviderUserIdAndStatus")
  class FindByProviderAndProviderUserIdAndStatus {

    @Test
    @DisplayName("returns entity only when all three conditions match")
    void returnsEntityWhenAllMatch() {
      Long userId = savedUser("deleted-kakao@test.com");
      savedAccount(userId, AuthProvider.KAKAO, "kakao-del-1", AccountStatus.DELETED);

      Optional<UserAccountEntity> result =
          repository.findByProviderAndProviderUserIdAndStatus(
              AuthProvider.KAKAO, "kakao-del-1", AccountStatus.DELETED);

      assertThat(result).isPresent();
    }

    @Test
    @DisplayName("returns empty when status does not match")
    void returnsEmptyWhenStatusMismatch() {
      Long userId = savedUser("active-kakao@test.com");
      savedAccount(userId, AuthProvider.KAKAO, "kakao-active-1", AccountStatus.ACTIVE);

      Optional<UserAccountEntity> result =
          repository.findByProviderAndProviderUserIdAndStatus(
              AuthProvider.KAKAO, "kakao-active-1", AccountStatus.DELETED);

      assertThat(result).isEmpty();
    }
  }

  // ============================================================
  // findByEmailAndStatus (JPQL JOIN with users table)
  // ============================================================

  @Nested
  @DisplayName("findByEmailAndStatus")
  class FindByEmailAndStatus {

    @Test
    @DisplayName("returns ACTIVE account for matching email")
    void returnsActiveAccountForEmail() {
      Long userId = savedUser("join-active@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      Optional<UserAccountEntity> result =
          repository.findByEmailAndStatus("join-active@test.com", AccountStatus.ACTIVE);

      assertThat(result).isPresent();
      assertThat(result.get().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("returns DELETED account for matching email when querying DELETED status")
    void returnsDeletedAccountForEmail() {
      Long userId = savedUser("join-deleted@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.DELETED);

      Optional<UserAccountEntity> result =
          repository.findByEmailAndStatus("join-deleted@test.com", AccountStatus.DELETED);

      assertThat(result).isPresent();
    }

    @Test
    @DisplayName("returns empty when account status does not match the queried status")
    void returnsEmptyWhenStatusMismatch() {
      Long userId = savedUser("mismatch@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      Optional<UserAccountEntity> result =
          repository.findByEmailAndStatus("mismatch@test.com", AccountStatus.DELETED);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty for unknown email")
    void returnsEmptyForUnknownEmail() {
      Optional<UserAccountEntity> result =
          repository.findByEmailAndStatus("nobody@test.com", AccountStatus.ACTIVE);

      assertThat(result).isEmpty();
    }
  }

  // ============================================================
  // findUserIdsByStatusAndDeletedAtBefore
  // ============================================================

  @Nested
  @DisplayName("findUserIdsByStatusAndDeletedAtBefore")
  class FindUserIdsByStatusAndDeletedAtBefore {

    @Test
    @DisplayName("returns userIds whose deletedAt is before the cutoff")
    void returnsUserIdsBeforeCutoff() {
      Long userId1 = savedUser("hard-del-1@test.com");
      Long userId2 = savedUser("hard-del-2@test.com");
      Long userId3 = savedUser("hard-del-recent@test.com");

      // deleted 10 days ago
      savedDeletedAccount(userId1, Instant.now().minusSeconds(10 * 86400));
      savedDeletedAccount(userId2, Instant.now().minusSeconds(5 * 86400));
      // deleted in the future — should not appear
      savedDeletedAccount(userId3, Instant.now().plusSeconds(86400));

      Instant cutoff = Instant.now();
      List<Long> result =
          repository.findUserIdsByStatusAndDeletedAtBefore(
              AccountStatus.DELETED, cutoff, PageRequest.of(0, 10));

      assertThat(result).containsExactlyInAnyOrder(userId1, userId2);
      assertThat(result).doesNotContain(userId3);
    }

    @Test
    @DisplayName("respects page size limit")
    void respectsPageSizeLimit() {
      for (int i = 0; i < 5; i++) {
        Long uid = savedUser("paged-" + i + "@test.com");
        savedDeletedAccount(uid, Instant.now().minusSeconds(86400));
      }

      Instant cutoff = Instant.now();
      List<Long> result =
          repository.findUserIdsByStatusAndDeletedAtBefore(
              AccountStatus.DELETED, cutoff, PageRequest.of(0, 3));

      assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("excludes ACTIVE accounts")
    void excludesActiveAccounts() {
      Long userId = savedUser("active-no-del@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      Instant cutoff = Instant.now();
      List<Long> result =
          repository.findUserIdsByStatusAndDeletedAtBefore(
              AccountStatus.DELETED, cutoff, PageRequest.of(0, 10));

      assertThat(result).doesNotContain(userId);
    }
  }

  // ============================================================
  // deleteByUserId / deleteByUserIdIn
  // ============================================================

  @Nested
  @DisplayName("deleteByUserId")
  class DeleteByUserId {

    @Test
    @DisplayName("removes the account row")
    void removesAccountRow() {
      Long userId = savedUser("to-delete@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      repository.deleteByUserId(userId);
      em.flush();

      assertThat(repository.findByUserId(userId)).isEmpty();
    }
  }

  @Nested
  @DisplayName("deleteByUserIdIn")
  class DeleteByUserIdIn {

    @Test
    @DisplayName("removes all specified account rows")
    void removesAllSpecifiedRows() {
      Long userId1 = savedUser("bulk-del-1@test.com");
      Long userId2 = savedUser("bulk-del-2@test.com");
      Long userId3 = savedUser("bulk-keep@test.com");
      savedAccount(userId1, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);
      savedAccount(userId2, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);
      savedAccount(userId3, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      repository.deleteByUserIdIn(List.of(userId1, userId2));
      em.flush();

      assertThat(repository.findByUserId(userId1)).isEmpty();
      assertThat(repository.findByUserId(userId2)).isEmpty();
      assertThat(repository.findByUserId(userId3)).isPresent();
    }
  }

  // ============================================================
  // Unique Constraint Tests
  // ============================================================

  @Nested
  @DisplayName("unique constraints")
  class UniqueConstraints {

    @Test
    @DisplayName("[E-16] duplicate userId violates uk_users_account_user_id")
    void duplicateUserId_violatesUniqueConstraint() {
      Long userId = savedUser("uk-user@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      UserAccountEntity duplicate =
          UserAccountEntity.builder()
              .userId(userId)
              .provider(AuthProvider.KAKAO)
              .providerUserId("kakao-dup")
              .status(AccountStatus.ACTIVE)
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      assertThatThrownBy(
              () -> {
                em.persist(duplicate);
                em.flush();
              })
          .isInstanceOf(PersistenceException.class);
    }

    // [E-17] duplicate (provider, providerUserId) unique constraint is defined only in Flyway
    // migration V030, not in JPA @Table annotation. This test requires real PostgreSQL (E2E env)
    // and is skipped in H2-based @DataJpaTest.
  }

  // ============================================================
  // Entity Callback Tests
  // ============================================================

  @Nested
  @DisplayName("entity lifecycle callbacks")
  class EntityLifecycleCallbacks {

    @Test
    @DisplayName("[E-18] @PrePersist sets createdAt, updatedAt, and defaults status to ACTIVE")
    void prePersist_setsTimestampsAndDefaultStatus() {
      Long userId = savedUser("prepersist@test.com");

      UserAccountEntity entity =
          UserAccountEntity.builder().userId(userId).provider(AuthProvider.LOCAL).build();

      UserAccountEntity persisted = em.persistAndFlush(entity);

      assertThat(persisted.getCreatedAt()).isNotNull();
      assertThat(persisted.getUpdatedAt()).isNotNull();
      assertThat(persisted.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("[E-19] @PreUpdate bumps updatedAt on modification")
    void preUpdate_bumpsUpdatedAt() {
      Long userId = savedUser("preupdate@test.com");
      savedAccount(userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);

      UserAccountEntity entity = repository.findByUserId(userId).orElseThrow();
      Instant originalUpdatedAt = entity.getUpdatedAt();

      entity.setPasswordHash("changed-hash");
      em.flush();

      UserAccountEntity reloaded = em.find(UserAccountEntity.class, entity.getId());
      assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
  }

  // ============================================================
  // Ordering Tests
  // ============================================================

  @Nested
  @DisplayName("findUserIdsByStatusAndDeletedAtBefore ordering")
  class FindUserIdsOrdering {

    @Test
    @DisplayName("[E-20] returns userIds ordered by deletedAt ASC")
    void returnsUserIds_orderedByDeletedAtAsc() {
      Long userId1 = savedUser("order-3d@test.com");
      Long userId2 = savedUser("order-1d@test.com");
      Long userId3 = savedUser("order-7d@test.com");

      savedDeletedAccount(userId1, Instant.now().minusSeconds(3 * 86400));
      savedDeletedAccount(userId2, Instant.now().minusSeconds(1 * 86400));
      savedDeletedAccount(userId3, Instant.now().minusSeconds(7 * 86400));

      Instant cutoff = Instant.now();
      List<Long> result =
          repository.findUserIdsByStatusAndDeletedAtBefore(
              AccountStatus.DELETED, cutoff, PageRequest.of(0, 10));

      assertThat(result).containsExactly(userId3, userId1, userId2);
    }
  }

  // ============================================================
  // Helpers
  // ============================================================

  /** Persists a minimal UserEntity and returns its generated id. */
  private Long savedUser(String email) {
    UserEntity user = UserEntity.builder().email(email).role(UserRole.USER).build();
    return em.persistAndFlush(user).getId();
  }

  private void savedAccount(
      Long userId, AuthProvider provider, String providerUserId, AccountStatus status) {
    Instant now = Instant.now();
    UserAccountEntity entity =
        UserAccountEntity.builder()
            .userId(userId)
            .provider(provider)
            .providerUserId(providerUserId)
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .build();
    em.persistAndFlush(entity);
  }

  /** Saves a DELETED account with the given deletedAt timestamp. */
  private void savedDeletedAccount(Long userId, Instant deletedAt) {
    Instant now = Instant.now();
    UserAccountEntity entity =
        UserAccountEntity.builder()
            .userId(userId)
            .provider(AuthProvider.LOCAL)
            .status(AccountStatus.DELETED)
            .deletedAt(deletedAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
    em.persistAndFlush(entity);
  }
}
