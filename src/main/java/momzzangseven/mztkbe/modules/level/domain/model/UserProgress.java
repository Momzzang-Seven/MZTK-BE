package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.NotEnoughXpException;

/** Domain model for user's level/XP state (cached). */
@Getter
@Builder(toBuilder = true)
public class UserProgress {
  private Long userId;
  private int level;
  private int availableXp;
  private int lifetimeXp;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static UserProgress createInitial(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }

    LocalDateTime now = LocalDateTime.now();
    return UserProgress.builder()
        .userId(userId)
        .level(1)
        .availableXp(0)
        .lifetimeXp(0)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public UserProgress grantXp(int amount, LocalDateTime at) {
    if (amount < 0) {
      throw new IllegalArgumentException("amount must be >= 0");
    }
    if (at == null) {
      throw new IllegalArgumentException("at must not be null");
    }

    return toBuilder()
        .availableXp(availableXp + amount)
        .lifetimeXp(lifetimeXp + amount)
        .updatedAt(at)
        .build();
  }

  public UserProgress levelUp(int requiredXp, LocalDateTime at) {
    if (requiredXp <= 0) {
      throw new IllegalArgumentException("requiredXp must be > 0");
    }
    if (at == null) {
      throw new IllegalArgumentException("at must not be null");
    }
    if (availableXp < requiredXp) {
      throw new NotEnoughXpException(
          "Not enough XP to level up: availableXp=" + availableXp + ", requiredXp=" + requiredXp);
    }

    return toBuilder().level(level + 1).availableXp(availableXp - requiredXp).updatedAt(at).build();
  }
}
