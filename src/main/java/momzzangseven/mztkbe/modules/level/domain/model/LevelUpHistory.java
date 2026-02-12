package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;

/** SSOT for level-ups and their reward processing status. */
@Getter
@Builder(access = AccessLevel.PRIVATE, toBuilder = true)
public class LevelUpHistory {
  private Long id;
  private Long userId;
  private Long levelPolicyId;
  private int fromLevel;
  private int toLevel;
  private int spentXp;
  private int rewardMztk;
  private LocalDateTime createdAt;

  public static LevelUpHistory initial(
      Long userId,
      Long levelPolicyId,
      int fromLevel,
      int toLevel,
      int spentXp,
      int rewardMztk,
      LocalDateTime createdAt) {
    validate(userId, levelPolicyId, fromLevel, toLevel, spentXp, rewardMztk, createdAt);
    return LevelUpHistory.builder()
        .userId(userId)
        .levelPolicyId(levelPolicyId)
        .fromLevel(fromLevel)
        .toLevel(toLevel)
        .spentXp(spentXp)
        .rewardMztk(rewardMztk)
        .createdAt(createdAt)
        .build();
  }

  public static LevelUpHistory createPending(
      Long userId, Long levelPolicyId, int fromLevel, int toLevel, int spentXp, int rewardMztk) {
    return initial(
        userId, levelPolicyId, fromLevel, toLevel, spentXp, rewardMztk, LocalDateTime.now());
  }

  public static LevelUpHistory reconstitute(
      Long id,
      Long userId,
      Long levelPolicyId,
      int fromLevel,
      int toLevel,
      int spentXp,
      int rewardMztk,
      LocalDateTime createdAt) {
    validate(userId, levelPolicyId, fromLevel, toLevel, spentXp, rewardMztk, createdAt);
    return LevelUpHistory.builder()
        .id(id)
        .userId(userId)
        .levelPolicyId(levelPolicyId)
        .fromLevel(fromLevel)
        .toLevel(toLevel)
        .spentXp(spentXp)
        .rewardMztk(rewardMztk)
        .createdAt(createdAt)
        .build();
  }

  private static void validate(
      Long userId,
      Long levelPolicyId,
      int fromLevel,
      int toLevel,
      int spentXp,
      int rewardMztk,
      LocalDateTime createdAt) {
    if (userId == null || userId <= 0) {
      throw new LevelUpCommandInvalidException("userId must be positive");
    }
    if (levelPolicyId == null || levelPolicyId <= 0) {
      throw new LevelUpCommandInvalidException("levelPolicyId must be positive");
    }
    if (fromLevel <= 0 || toLevel <= 0 || toLevel <= fromLevel) {
      throw new LevelUpCommandInvalidException("fromLevel/toLevel are invalid");
    }
    if (spentXp < 0) {
      throw new LevelUpCommandInvalidException("spentXp must be >= 0");
    }
    if (rewardMztk < 0) {
      throw new LevelUpCommandInvalidException("rewardMztk must be >= 0");
    }
    if (createdAt == null) {
      throw new LevelUpCommandInvalidException("createdAt is required");
    }
  }
}
