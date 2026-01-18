package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

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
}