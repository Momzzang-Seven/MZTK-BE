package momzzangseven.mztkbe.modules.account.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository.UserAccountJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAccountPersistenceAdapter unit test")
class UserAccountPersistenceAdapterTest {

  @Mock private UserAccountJpaRepository userAccountJpaRepository;

  private UserAccountPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new UserAccountPersistenceAdapter(userAccountJpaRepository);
  }

  // ============================================================
  // LoadUserAccountPort
  // ============================================================

  @Nested
  @DisplayName("findByUserId")
  class FindByUserId {

    @Test
    @DisplayName("returns mapped domain when entity exists")
    void returnsDomainWhenExists() {
      UserAccountEntity entity = activeLocalEntity(1L, 10L);
      when(userAccountJpaRepository.findByUserId(10L)).thenReturn(Optional.of(entity));

      Optional<UserAccount> result = adapter.findByUserId(10L);

      assertThat(result).isPresent();
      assertThat(result.get().getId()).isEqualTo(1L);
      assertThat(result.get().getUserId()).isEqualTo(10L);
      assertThat(result.get().getProvider()).isEqualTo(AuthProvider.LOCAL);
      assertThat(result.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("returns empty when entity does not exist")
    void returnsEmptyWhenMissing() {
      when(userAccountJpaRepository.findByUserId(99L)).thenReturn(Optional.empty());

      assertThat(adapter.findByUserId(99L)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByUserIdForUpdate")
  class FindByUserIdForUpdate {

    @Test
    @DisplayName("uses locked repository query and returns mapped domain when entity exists")
    void returnsDomainWhenExists() {
      UserAccountEntity entity = activeLocalEntity(1L, 10L);
      when(userAccountJpaRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(entity));

      Optional<UserAccount> result = adapter.findByUserIdForUpdate(10L);

      assertThat(result).isPresent();
      assertThat(result.get().getUserId()).isEqualTo(10L);
      verify(userAccountJpaRepository).findByUserIdForUpdate(10L);
    }
  }

  @Nested
  @DisplayName("findByProviderAndProviderUserId")
  class FindByProviderAndProviderUserId {

    @Test
    @DisplayName("returns mapped domain when entity exists")
    void returnsDomainWhenExists() {
      UserAccountEntity entity =
          baseEntity(2L, 20L, AuthProvider.KAKAO, "kakao-abc", AccountStatus.ACTIVE);
      when(userAccountJpaRepository.findByProviderAndProviderUserId(
              AuthProvider.KAKAO, "kakao-abc"))
          .thenReturn(Optional.of(entity));

      Optional<UserAccount> result =
          adapter.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-abc");

      assertThat(result).isPresent();
      assertThat(result.get().getProviderUserId()).isEqualTo("kakao-abc");
    }
  }

  @Nested
  @DisplayName("findUserIdsForHardDeletion")
  class FindUserIdsForHardDeletion {

    @Test
    @DisplayName("rejects limit <= 0")
    void rejectsNonPositiveLimit() {
      Instant cutoff = Instant.now();

      assertThatThrownBy(() -> adapter.findUserIdsForHardDeletion(cutoff, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit must be > 0");

      assertThatThrownBy(() -> adapter.findUserIdsForHardDeletion(cutoff, -1))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("delegates to repository with DELETED status and PageRequest")
    void delegatesWithCorrectArguments() {
      Instant cutoff = Instant.now();
      when(userAccountJpaRepository.findUserIdsByStatusAndDeletedAtBefore(
              any(), any(), any(PageRequest.class)))
          .thenReturn(List.of(5L, 6L));

      List<Long> result = adapter.findUserIdsForHardDeletion(cutoff, 10);

      assertThat(result).containsExactly(5L, 6L);
      ArgumentCaptor<AccountStatus> statusCaptor = ArgumentCaptor.forClass(AccountStatus.class);
      ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
      verify(userAccountJpaRepository)
          .findUserIdsByStatusAndDeletedAtBefore(
              statusCaptor.capture(), any(), pageCaptor.capture());
      assertThat(statusCaptor.getValue()).isEqualTo(AccountStatus.DELETED);
      assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(10);
    }
  }

  // ============================================================
  // SaveUserAccountPort
  // ============================================================

  @Nested
  @DisplayName("save")
  class Save {

    @Test
    @DisplayName("new account (id == null) is inserted via toEntity path")
    void insertsNewAccount() {
      UserAccount newAccount = UserAccount.createLocal(10L, "$2a$hash");
      UserAccountEntity saved = activeLocalEntity(100L, 10L);
      when(userAccountJpaRepository.save(any())).thenReturn(saved);

      UserAccount result = adapter.save(newAccount);

      ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
      verify(userAccountJpaRepository).save(captor.capture());
      UserAccountEntity captured = captor.getValue();
      assertThat(captured.getUserId()).isEqualTo(10L);
      assertThat(captured.getProvider()).isEqualTo(AuthProvider.LOCAL);
      assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("existing account (id != null) updates the fetched entity")
    void updatesExistingAccount() {
      UserAccountEntity existing = activeLocalEntity(1L, 10L);
      when(userAccountJpaRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(userAccountJpaRepository.save(any())).thenReturn(existing);

      UserAccount domain =
          UserAccount.builder()
              .id(1L)
              .userId(10L)
              .provider(AuthProvider.LOCAL)
              .passwordHash("new-hash")
              .status(AccountStatus.ACTIVE)
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      adapter.save(domain);

      verify(userAccountJpaRepository).findById(1L);
      assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    @DisplayName("throws when existing id is not found in DB")
    void throwsWhenIdNotFound() {
      when(userAccountJpaRepository.findById(99L)).thenReturn(Optional.empty());

      UserAccount phantom =
          UserAccount.builder()
              .id(99L)
              .userId(1L)
              .provider(AuthProvider.LOCAL)
              .status(AccountStatus.ACTIVE)
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      assertThatThrownBy(() -> adapter.save(phantom))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("99");
    }
  }

  @Nested
  @DisplayName("save — edge cases")
  class SaveEdgeCases {

    @Test
    @DisplayName("[M-30] new account with null status defaults to ACTIVE in toEntity")
    void newAccountWithNullStatus_defaultsToActive() {
      UserAccount nullStatus =
          UserAccount.builder()
              .userId(10L)
              .provider(AuthProvider.LOCAL)
              .passwordHash("hash")
              .status(null)
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      UserAccountEntity savedEntity = activeLocalEntity(100L, 10L);
      when(userAccountJpaRepository.save(any())).thenReturn(savedEntity);

      adapter.save(nullStatus);

      ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
      verify(userAccountJpaRepository).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("[M-31] updateEntityFromDomain copies all mutable fields")
    void updateEntityFromDomain_copiesAllFields() {
      Instant now = Instant.now();
      UserAccountEntity existing = activeLocalEntity(1L, 10L);
      when(userAccountJpaRepository.findById(1L)).thenReturn(Optional.of(existing));
      when(userAccountJpaRepository.save(any())).thenReturn(existing);

      UserAccount domain =
          UserAccount.builder()
              .id(1L)
              .userId(10L)
              .provider(AuthProvider.GOOGLE)
              .providerUserId("google-new")
              .passwordHash("updated-hash")
              .googleRefreshToken("new-refresh-token")
              .lastLoginAt(now)
              .status(AccountStatus.DELETED)
              .deletedAt(now)
              .createdAt(now)
              .updatedAt(now)
              .build();

      adapter.save(domain);

      assertThat(existing.getProvider()).isEqualTo(AuthProvider.GOOGLE);
      assertThat(existing.getProviderUserId()).isEqualTo("google-new");
      assertThat(existing.getPasswordHash()).isEqualTo("updated-hash");
      assertThat(existing.getGoogleRefreshToken()).isEqualTo("new-refresh-token");
      assertThat(existing.getLastLoginAt()).isEqualTo(now);
      assertThat(existing.getStatus()).isEqualTo(AccountStatus.DELETED);
      assertThat(existing.getDeletedAt()).isEqualTo(now);
      assertThat(existing.getUpdatedAt()).isEqualTo(now);
    }
  }

  // ============================================================
  // DeleteUserAccountPort
  // ============================================================

  @Nested
  @DisplayName("deleteByUserId")
  class DeleteByUserId {

    @Test
    @DisplayName("delegates to repository")
    void delegatesToRepository() {
      adapter.deleteByUserId(5L);

      verify(userAccountJpaRepository).deleteByUserId(5L);
    }
  }

  @Nested
  @DisplayName("deleteByUserIdIn")
  class DeleteByUserIdIn {

    @Test
    @DisplayName("delegates list to repository")
    void delegatesListToRepository() {
      List<Long> ids = List.of(1L, 2L, 3L);

      adapter.deleteByUserIdIn(ids);

      verify(userAccountJpaRepository).deleteByUserIdIn(ids);
    }
  }

  // ============================================================
  // Helpers
  // ============================================================

  private UserAccountEntity activeLocalEntity(Long id, Long userId) {
    return baseEntity(id, userId, AuthProvider.LOCAL, null, AccountStatus.ACTIVE);
  }

  private UserAccountEntity baseEntity(
      Long id, Long userId, AuthProvider provider, String providerUserId, AccountStatus status) {
    Instant now = Instant.now();
    return UserAccountEntity.builder()
        .id(id)
        .userId(userId)
        .provider(provider)
        .providerUserId(providerUserId)
        .status(status)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
