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
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
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

  // ─────────────────────────────────────────────────────────────────────────
  // isDeletedUser() - line 142 (both branches uncovered)
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("loadDeletedUserByEmail - DELETED 엔티티는 반환")
  void loadDeletedUserByEmail_withDeletedEntity_returnsDomain() {
    UserEntity entity = baseEntity(3L, UserStatus.DELETED);
    when(userJpaRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(entity));

    Optional<User> found = adapter.loadDeletedUserByEmail("deleted@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(UserStatus.DELETED);
  }

  @Test
  @DisplayName("loadDeletedUserByEmail - ACTIVE 엔티티는 빈 결과 반환")
  void loadDeletedUserByEmail_withActiveEntity_returnsEmpty() {
    UserEntity entity = baseEntity(4L, UserStatus.ACTIVE);
    when(userJpaRepository.findByEmail("active@example.com")).thenReturn(Optional.of(entity));

    Optional<User> found = adapter.loadDeletedUserByEmail("active@example.com");

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("loadDeletedUserById - DELETED 엔티티는 반환")
  void loadDeletedUserById_withDeletedEntity_returnsDomain() {
    UserEntity entity = baseEntity(5L, UserStatus.DELETED);
    when(userJpaRepository.findById(5L)).thenReturn(Optional.of(entity));

    Optional<User> found = adapter.loadDeletedUserById(5L);

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(UserStatus.DELETED);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // mapToEntity() 분기 (line 178, 179, 196)
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("saveUser - providerUserId가 blank이면 LOCAL 접두사로 파생")
  void saveUser_withBlankProviderUserId_derivesFromEmail() {
    User user =
        User.builder()
            .id(null)
            .email("blank@example.com")
            .authProvider(AuthProvider.LOCAL)
            .providerUserId("   ")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    when(userJpaRepository.save(any(UserEntity.class)))
        .thenAnswer(
            invocation -> {
              UserEntity e = invocation.getArgument(0);
              e.setId(60L);
              return e;
            });

    adapter.saveUser(user);

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userJpaRepository).save(captor.capture());
    assertThat(captor.getValue().getProviderUserId()).isEqualTo("LOCAL:blank@example.com");
  }

  @Test
  @DisplayName("saveUser - providerUserId가 null이지만 KAKAO이면 파생하지 않음")
  void saveUser_withNullProviderUserIdAndKakao_doesNotDerive() {
    User user =
        User.builder()
            .id(null)
            .email("kakao@example.com")
            .authProvider(AuthProvider.KAKAO)
            .providerUserId(null)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    when(userJpaRepository.save(any(UserEntity.class)))
        .thenAnswer(
            invocation -> {
              UserEntity e = invocation.getArgument(0);
              e.setId(61L);
              return e;
            });

    adapter.saveUser(user);

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userJpaRepository).save(captor.capture());
    assertThat(captor.getValue().getProviderUserId()).isNull();
  }

  @Test
  @DisplayName("saveUser - DELETED 신규 유저 생성 시 deletedAt 보존")
  void saveUser_withNewDeletedUser_preservesDeletedAt() {
    LocalDateTime deletedAt = LocalDateTime.of(2026, 2, 20, 10, 0);
    User user =
        User.builder()
            .id(null)
            .email("del@example.com")
            .authProvider(AuthProvider.KAKAO)
            .providerUserId("kakao-del")
            .role(UserRole.USER)
            .status(UserStatus.DELETED)
            .deletedAt(deletedAt)
            .build();

    when(userJpaRepository.save(any(UserEntity.class)))
        .thenAnswer(
            invocation -> {
              UserEntity e = invocation.getArgument(0);
              e.setId(62L);
              return e;
            });

    adapter.saveUser(user);

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userJpaRepository).save(captor.capture());
    assertThat(captor.getValue().getDeletedAt()).isEqualTo(deletedAt);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // updateEntityFromDomain() 분기 (line 205) - null status → ACTIVE 폴백
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("saveUser (update) - status=null이면 ACTIVE로 폴백")
  void saveUser_updateWithNullStatus_defaultsToActive() {
    UserEntity managed = baseEntity(10L, UserStatus.ACTIVE);
    when(userJpaRepository.findById(10L)).thenReturn(Optional.of(managed));
    when(userJpaRepository.save(managed)).thenReturn(managed);

    User update =
        User.builder()
            .id(10L)
            .email("u@example.com")
            .authProvider(AuthProvider.LOCAL)
            .providerUserId("LOCAL:u@example.com")
            .role(UserRole.USER)
            .status(null)
            .build();

    adapter.saveUser(update);

    assertThat(managed.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(managed.getDeletedAt()).isNull();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // resolveStatus() 분기 (line 223, 226) - null status 엔티티
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("loadUserByEmail - status=null & deletedAt=null → ACTIVE로 조회")
  void loadUserByEmail_nullStatusNullDeletedAt_treatedAsActive() {
    UserEntity entityWithNullStatus =
        UserEntity.builder()
            .id(7L)
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-7")
            .email("null-status@example.com")
            .passwordHash("$2a$" + "d".repeat(56))
            .role(UserRole.USER)
            .nickname("null-status")
            .status(null)
            .deletedAt(null)
            .build();

    when(userJpaRepository.findByEmail("null-status@example.com"))
        .thenReturn(Optional.of(entityWithNullStatus));

    Optional<User> found = adapter.loadUserByEmail("null-status@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("loadUserByEmail - status=null & deletedAt!=null → DELETED로 조회 (필터됨)")
  void loadUserByEmail_nullStatusNonNullDeletedAt_treatedAsDeleted() {
    LocalDateTime deletedAt = LocalDateTime.of(2026, 2, 10, 0, 0);
    UserEntity entityWithNullStatus =
        UserEntity.builder()
            .id(8L)
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-8")
            .email("null-del@example.com")
            .passwordHash("$2a$" + "d".repeat(56))
            .role(UserRole.USER)
            .nickname("null-del")
            .status(null)
            .deletedAt(deletedAt)
            .build();

    when(userJpaRepository.findByEmail("null-del@example.com"))
        .thenReturn(Optional.of(entityWithNullStatus));

    // loadUserByEmail filters for ACTIVE; a null-status entity with deletedAt set is DELETED
    Optional<User> found = adapter.loadUserByEmail("null-del@example.com");

    assertThat(found).isEmpty();
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
