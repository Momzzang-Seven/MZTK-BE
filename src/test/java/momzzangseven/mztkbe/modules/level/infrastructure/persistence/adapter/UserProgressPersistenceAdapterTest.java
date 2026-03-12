package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.UserProgressEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.UserProgressJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class UserProgressPersistenceAdapterTest {

  @Mock private UserProgressJpaRepository userProgressJpaRepository;
  @Mock private PlatformTransactionManager transactionManager;

  @InjectMocks private UserProgressPersistenceAdapter adapter;

  @Test
  void loadUserProgress_shouldMapOptionalEntity() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    when(userProgressJpaRepository.findById(1L))
        .thenReturn(
            Optional.of(
                UserProgressEntity.builder()
                    .userId(1L)
                    .level(2)
                    .availableXp(100)
                    .lifetimeXp(300)
                    .createdAt(now)
                    .updatedAt(now)
                    .build()));

    Optional<UserProgress> result = adapter.loadUserProgress(1L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getLevel()).isEqualTo(2);
  }

  @Test
  void loadUserProgressWithLock_shouldThrowWhenMissing() {
    when(userProgressJpaRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.loadUserProgressWithLock(1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UserProgress not found");
  }

  @Test
  void loadOrCreateUserProgress_shouldReturnExistingWithoutCreation() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgressEntity existing =
        UserProgressEntity.builder()
            .userId(1L)
            .level(1)
            .availableXp(0)
            .lifetimeXp(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
    when(userProgressJpaRepository.findById(1L)).thenReturn(Optional.of(existing));

    UserProgress result = adapter.loadOrCreateUserProgress(1L);

    assertThat(result.getUserId()).isEqualTo(1L);
    verify(userProgressJpaRepository).findById(1L);
  }

  @Test
  void saveUserProgress_shouldUpdateExistingEntityFields() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(1L)
            .level(3)
            .availableXp(200)
            .lifetimeXp(400)
            .createdAt(now.minusDays(1))
            .updatedAt(now)
            .build();
    UserProgressEntity existing =
        UserProgressEntity.builder()
            .userId(1L)
            .level(1)
            .availableXp(0)
            .lifetimeXp(0)
            .createdAt(now.minusDays(2))
            .updatedAt(now.minusDays(2))
            .build();

    when(userProgressJpaRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userProgressJpaRepository.save(any(UserProgressEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    UserProgress saved = adapter.saveUserProgress(progress);

    assertThat(saved.getLevel()).isEqualTo(3);
    assertThat(saved.getAvailableXp()).isEqualTo(200);

    ArgumentCaptor<UserProgressEntity> captor = ArgumentCaptor.forClass(UserProgressEntity.class);
    verify(userProgressJpaRepository).save(captor.capture());
    assertThat(captor.getValue().getLifetimeXp()).isEqualTo(400);
  }
}
