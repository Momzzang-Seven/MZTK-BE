package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPersistenceAdapter unit test")
class UserPersistenceAdapterTest {

  @Mock private UserJpaRepository userJpaRepository;

  private UserPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new UserPersistenceAdapter(userJpaRepository);
  }

  @Test
  @DisplayName("loadUserByEmail returns mapped domain for active entity")
  void loadUserByEmail_withActiveEntity_returnsDomain() {
    UserEntity entity = baseEntity(1L, UserStatus.ACTIVE);
    when(userJpaRepository.findByEmail("active@example.com")).thenReturn(Optional.of(entity));

    Optional<User> found = adapter.loadUserByEmail("active@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(1L);
    assertThat(found.get().getEmail()).isEqualTo("active@example.com");
    assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("loadUserByEmail filters out deleted entity")
  void loadUserByEmail_withDeletedEntity_returnsEmpty() {
    UserEntity entity = baseEntity(2L, UserStatus.DELETED);
    when(userJpaRepository.findByEmail("active@example.com")).thenReturn(Optional.of(entity));

    Optional<User> found = adapter.loadUserByEmail("active@example.com");

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("saveUser creates new local entity with derived provider id")
  void saveUser_withNewLocalUser_derivesProviderUserId() {
    User newLocalUser =
        User.builder()
            .id(null)
            .email("local@example.com")
            .password("$2a$" + "b".repeat(56))
            .nickname("local")
            .authProvider(AuthProvider.LOCAL)
            .providerUserId(null)
            .role(UserRole.USER)
            .status(null)
            .createdAt(LocalDateTime.of(2026, 2, 1, 10, 0))
            .updatedAt(LocalDateTime.of(2026, 2, 1, 10, 0))
            .build();

    when(userJpaRepository.save(any(UserEntity.class)))
        .thenAnswer(
            invocation -> {
              UserEntity incoming = invocation.getArgument(0);
              incoming.setId(50L);
              return incoming;
            });

    User saved = adapter.saveUser(newLocalUser);

    ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userJpaRepository).save(entityCaptor.capture());
    UserEntity persisted = entityCaptor.getValue();
    assertThat(persisted.getProviderUserId()).isEqualTo("LOCAL:local@example.com");
    assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(persisted.getDeletedAt()).isNull();

    assertThat(saved.getId()).isEqualTo(50L);
    assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("saveUser updates managed entity when user id exists")
  void saveUser_withExistingUser_updatesManagedEntity() {
    UserEntity managed = baseEntity(10L, UserStatus.ACTIVE);
    when(userJpaRepository.findById(10L)).thenReturn(Optional.of(managed));
    when(userJpaRepository.save(managed)).thenReturn(managed);

    LocalDateTime deletedAt = LocalDateTime.of(2026, 2, 20, 9, 0);
    User update =
        User.builder()
            .id(10L)
            .email("updated@example.com")
            .password("$2a$" + "c".repeat(56))
            .nickname("updated-nick")
            .profileImageUrl("https://image")
            .providerUserId("updated-provider-id")
            .googleRefreshToken("token")
            .walletAddress("0x0000000000000000000000000000000000000001")
            .authProvider(AuthProvider.GOOGLE)
            .role(UserRole.TRAINER)
            .status(UserStatus.DELETED)
            .deletedAt(deletedAt)
            .lastLoginAt(LocalDateTime.of(2026, 2, 18, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 2, 28, 9, 0))
            .build();

    User result = adapter.saveUser(update);

    assertThat(managed.getEmail()).isEqualTo("updated@example.com");
    assertThat(managed.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(managed.getRole()).isEqualTo(UserRole.TRAINER);
    assertThat(managed.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(managed.getDeletedAt()).isEqualTo(deletedAt);
    assertThat(managed.getProviderUserId()).isEqualTo("updated-provider-id");

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(result.getEmail()).isEqualTo("updated@example.com");
    assertThat(result.getStatus()).isEqualTo(UserStatus.DELETED);
  }

  @Test
  @DisplayName("saveUser throws when existing user id is missing in repository")
  void saveUser_withMissingExistingUser_throws() {
    User update =
        User.builder()
            .id(99L)
            .email("missing@example.com")
            .authProvider(AuthProvider.LOCAL)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();
    when(userJpaRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.saveUser(update))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User not found with ID: 99");
  }

  @Test
  @DisplayName("loadUserIdsForDeletion rejects non-positive limit")
  void loadUserIdsForDeletion_withInvalidLimit_throws() {
    assertThatThrownBy(
            () ->
                adapter.loadUserIdsForDeletion(
                    UserStatus.DELETED, LocalDateTime.of(2026, 2, 28, 10, 0), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be > 0");
  }

  @Test
  @DisplayName("loadUserIdsForDeletion delegates with pageable limit")
  void loadUserIdsForDeletion_delegatesToRepository() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 2, 28, 10, 0);
    when(userJpaRepository.findIdsByStatusAndDeletedAtBefore(
            eq(UserStatus.DELETED), eq(cutoff), any(Pageable.class)))
        .thenReturn(List.of(4L, 6L));

    List<Long> ids = adapter.loadUserIdsForDeletion(UserStatus.DELETED, cutoff, 2);

    assertThat(ids).containsExactly(4L, 6L);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(userJpaRepository)
        .findIdsByStatusAndDeletedAtBefore(
            eq(UserStatus.DELETED), eq(cutoff), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
  }

  @Test
  @DisplayName("deleteAllByIdInBatch delegates directly")
  void deleteAllByIdInBatch_delegates() {
    List<Long> ids = List.of(1L, 2L, 3L);

    adapter.deleteAllByIdInBatch(ids);

    verify(userJpaRepository).deleteAllByIdInBatch(ids);
  }

  private UserEntity baseEntity(Long id, UserStatus status) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    return UserEntity.builder()
        .id(id)
        .provider(AuthProvider.KAKAO)
        .providerUserId("provider-user")
        .email("active@example.com")
        .passwordHash("$2a$" + "d".repeat(56))
        .role(UserRole.USER)
        .nickname("nick")
        .status(status)
        .deletedAt(status == UserStatus.DELETED ? now.minusDays(1) : null)
        .createdAt(now.minusDays(30))
        .updatedAt(now.minusDays(1))
        .lastLoginAt(now.minusDays(2))
        .build();
  }
}
