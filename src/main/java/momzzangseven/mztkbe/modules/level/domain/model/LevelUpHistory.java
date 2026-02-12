package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** SSOT for level-ups and their reward processing status. */
@Getter
@Builder(toBuilder = true)
public class LevelUpHistory {
  private Long id;
  private Long userId;
  private Long levelPolicyId;
  private int fromLevel;
  private int toLevel;
  private int spentXp;
  private int rewardMztk;
  private LocalDateTime createdAt;

  public static LevelUpHistory createPending(
      Long userId, Long levelPolicyId, int fromLevel, int toLevel, int spentXp, int rewardMztk) {
    return LevelUpHistory.builder()
        .userId(userId)
        .levelPolicyId(levelPolicyId)
        .fromLevel(fromLevel)
        .toLevel(toLevel)
        .spentXp(spentXp)
        .rewardMztk(rewardMztk)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
