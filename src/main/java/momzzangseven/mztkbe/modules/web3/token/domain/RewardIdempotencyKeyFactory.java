package momzzangseven.mztkbe.modules.web3.token.domain;

/** Policy-fixed idempotency key factory for reward issuance. */
public final class RewardIdempotencyKeyFactory {

  private RewardIdempotencyKeyFactory() {}

  public static String forLevelUpReward(Long userId, Long levelUpHistoryId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (levelUpHistoryId == null || levelUpHistoryId <= 0) {
      throw new IllegalArgumentException("levelUpHistoryId must be positive");
    }
    return "reward:" + userId + ":" + levelUpHistoryId;
  }
}
