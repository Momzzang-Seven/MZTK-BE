package momzzangseven.mztkbe.modules.level.application.dto;

import lombok.Builder;

/** Query result for {@code GET /users/me/level}. */
@Builder
public record MyLevelResult(
    int level, int availableXp, int requiredXpForNext, int rewardMztkForNext) {

  public void validate() {
    if (level < 1) {
      throw new IllegalStateException("Level must be >= 1");
    }
    if (availableXp < 0) {
      throw new IllegalStateException("Available XP must be >= 0");
    }
    if (requiredXpForNext < 0) {
      throw new IllegalStateException("Required XP must be >= 0");
    }
    if (rewardMztkForNext < 0) {
      throw new IllegalStateException("Reward MZTK must be >= 0");
    }
  }
}
