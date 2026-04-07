package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  @DisplayName("loadUserByEmail returns mapped domain when entity exists")
  void loadUserByEmail_withEntity_returnsDomain() {
    UserEntity entity = baseEntity(1L);
    when(userJpaRepository.findByEmail("active@example.com")).thenReturn(Optional.of(entity));

    Optional<User> found = adapter.loadUserByEmail("active@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(1L);
    assertThat(found.get().getEmail()).isEqualTo("active@example.com");
  }

  @Test
  @DisplayName("loadUserByEmail returns empty when no entity found")
  void loadUserByEmail_withNoEntity_returnsEmpty() {
    when(userJpaRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

    Optional<User> found = adapter.loadUserByEmail("missing@example.com");

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("loadUserById returns mapped domain when entity exists")
  void loadUserById_withEntity_returnsDomain() {
    UserEntity entity = baseEntity(1L);
    when(userJpaRepository.findById(1L)).thenReturn(Optional.of(entity));

    Optional<User> found = adapter.loadUserById(1L);

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(1L);
    assertThat(found.get().getEmail()).isEqualTo("active@example.com");
  }

  @Test
  @DisplayName("loadUserById returns empty when no entity found")
  void loadUserById_withNoEntity_returnsEmpty() {
    when(userJpaRepository.findById(99L)).thenReturn(Optional.empty());

    Optional<User> found = adapter.loadUserById(99L);

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("saveUser creates new entity when user id is null")
  void saveUser_withNewUser_createsEntity() {
    User newUser =
        User.builder()
            .id(null)
            .email("new@example.com")
            .nickname("new-nick")
            .role(UserRole.USER)
            .createdAt(Instant.parse("2026-02-01T10:00:00Z"))
            .updatedAt(Instant.parse("2026-02-01T10:00:00Z"))
            .build();

    when(userJpaRepository.save(any(UserEntity.class)))
        .thenAnswer(
            invocation -> {
              UserEntity incoming = invocation.getArgument(0);
              incoming.setId(50L);
              return incoming;
            });

    User saved = adapter.saveUser(newUser);

    ArgumentCaptor<UserEntity> entityCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userJpaRepository).save(entityCaptor.capture());
    UserEntity persisted = entityCaptor.getValue();
    assertThat(persisted.getEmail()).isEqualTo("new@example.com");
    assertThat(persisted.getNickname()).isEqualTo("new-nick");
    assertThat(persisted.getRole()).isEqualTo(UserRole.USER);

    assertThat(saved.getId()).isEqualTo(50L);
    assertThat(saved.getEmail()).isEqualTo("new@example.com");
  }

  @Test
  @DisplayName("saveUser updates managed entity when user id exists")
  void saveUser_withExistingUser_updatesManagedEntity() {
    UserEntity managed = baseEntity(10L);
    when(userJpaRepository.findById(10L)).thenReturn(Optional.of(managed));
    when(userJpaRepository.save(managed)).thenReturn(managed);

    User update =
        User.builder()
            .id(10L)
            .email("updated@example.com")
            .nickname("updated-nick")
            .profileImageUrl("https://image")
            .role(UserRole.TRAINER)
            .updatedAt(Instant.parse("2026-02-28T09:00:00Z"))
            .build();

    User result = adapter.saveUser(update);

    assertThat(managed.getEmail()).isEqualTo("updated@example.com");
    assertThat(managed.getNickname()).isEqualTo("updated-nick");
    assertThat(managed.getRole()).isEqualTo(UserRole.TRAINER);

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(result.getEmail()).isEqualTo("updated@example.com");
  }

  @Test
  @DisplayName("saveUser throws when existing user id is missing in repository")
  void saveUser_withMissingExistingUser_throws() {
    User update = User.builder().id(99L).email("missing@example.com").role(UserRole.USER).build();
    when(userJpaRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.saveUser(update))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User not found with ID: 99");
  }

  @Test
  @DisplayName("deleteAllByIdInBatch delegates directly")
  void deleteAllByIdInBatch_delegates() {
    List<Long> ids = List.of(1L, 2L, 3L);

    adapter.deleteAllByIdInBatch(ids);

    verify(userJpaRepository).deleteAllByIdInBatch(ids);
  }

  private UserEntity baseEntity(Long id) {
    Instant now = Instant.parse("2026-02-28T09:00:00Z");
    return UserEntity.builder()
        .id(id)
        .email("active@example.com")
        .role(UserRole.USER)
        .nickname("nick")
        .createdAt(now.minus(30, ChronoUnit.DAYS))
        .updatedAt(now.minus(1, ChronoUnit.DAYS))
        .build();
  }
}
