package momzzangseven.mztkbe.modules.level.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.level.NotEnoughXpException;
import org.junit.jupiter.api.Test;

class UserProgressTest {

  @Test
  void createInitial_shouldThrowWhenUserIdIsNotPositive() {
    assertThatThrownBy(() -> UserProgress.createInitial(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  void createInitial_shouldThrowWhenUserIdNull() {
    assertThatThrownBy(() -> UserProgress.createInitial(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  void createInitial_shouldThrowWhenUserIdNegative() {
    assertThatThrownBy(() -> UserProgress.createInitial(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  void createInitial_shouldCreateProgressWhenValidUserId() {
    UserProgress progress = UserProgress.createInitial(1L);

    assertThat(progress.getUserId()).isEqualTo(1L);
    assertThat(progress.getLevel()).isEqualTo(1);
  }

  @Test
  void grantXp_shouldIncreaseAvailableAndLifetimeXp() {
    UserProgress progress = baseProgress(1L, 2, 10, 100);
    LocalDateTime at = LocalDateTime.of(2026, 2, 26, 10, 0);

    UserProgress updated = progress.grantXp(30, at);

    assertThat(updated.getAvailableXp()).isEqualTo(40);
    assertThat(updated.getLifetimeXp()).isEqualTo(130);
    assertThat(updated.getUpdatedAt()).isEqualTo(at);
  }

  @Test
  void grantXp_shouldThrowWhenAmountIsNegative() {
    UserProgress progress = baseProgress(1L, 1, 0, 0);

    assertThatThrownBy(() -> progress.grantXp(-1, LocalDateTime.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount must be >= 0");
  }

  @Test
  void grantXp_shouldThrowWhenAtNull() {
    UserProgress progress = baseProgress(1L, 1, 0, 0);

    assertThatThrownBy(() -> progress.grantXp(1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at must not be null");
  }

  @Test
  void levelUp_shouldThrowWhenAvailableXpIsInsufficient() {
    UserProgress progress = baseProgress(1L, 3, 49, 200);

    assertThatThrownBy(() -> progress.levelUp(50, LocalDateTime.now()))
        .isInstanceOf(NotEnoughXpException.class)
        .hasMessageContaining("Not enough XP");
  }

  @Test
  void levelUp_shouldConsumeXpAndIncreaseLevel() {
    UserProgress progress = baseProgress(1L, 3, 120, 300);
    LocalDateTime at = LocalDateTime.of(2026, 2, 26, 12, 30);

    UserProgress updated = progress.levelUp(100, at);

    assertThat(updated.getLevel()).isEqualTo(4);
    assertThat(updated.getAvailableXp()).isEqualTo(20);
    assertThat(updated.getUpdatedAt()).isEqualTo(at);
  }

  @Test
  void levelUp_shouldThrowWhenRequiredXpNonPositive() {
    UserProgress progress = baseProgress(1L, 3, 120, 300);

    assertThatThrownBy(() -> progress.levelUp(0, LocalDateTime.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requiredXp must be > 0");
  }

  @Test
  void levelUp_shouldThrowWhenAtNull() {
    UserProgress progress = baseProgress(1L, 3, 120, 300);

    assertThatThrownBy(() -> progress.levelUp(100, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at must not be null");
  }

  private UserProgress baseProgress(Long userId, int level, int availableXp, int lifetimeXp) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 26, 0, 0);
    return UserProgress.builder()
        .userId(userId)
        .level(level)
        .availableXp(availableXp)
        .lifetimeXp(lifetimeXp)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
