package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.LevelValidationMessage;

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

  /** If reward amount is positive, transaction reference must point to this history id. */
  public void assertRewardTransactionLink(Long transactionReferenceId) {
    if (rewardMztk <= 0) {
      return;
    }
    if (id == null || id <= 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.REFERENCE_ID_POSITIVE);
    }
    if (transactionReferenceId == null || !id.equals(transactionReferenceId)) {
      throw new LevelUpCommandInvalidException(
          "reward transaction reference must match levelUpHistory.id");
    }
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
      throw new LevelUpCommandInvalidException(LevelValidationMessage.USER_ID_POSITIVE);
    }
    if (levelPolicyId == null || levelPolicyId <= 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.LEVEL_POLICY_ID_POSITIVE);
    }
    if (fromLevel <= 0 || toLevel <= 0 || toLevel <= fromLevel) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.LEVEL_RANGE_INVALID);
    }
    if (spentXp < 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.SPENT_XP_NON_NEGATIVE);
    }
    if (rewardMztk < 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.REWARD_MZTK_NON_NEGATIVE);
    }
    if (createdAt == null) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.CREATED_AT_REQUIRED);
    }
  }
}
