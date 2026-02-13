package momzzangseven.mztkbe.modules.web3.transfer.domain.support;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Policy-fixed idempotency key factory for reward issuance. */
public final class RewardIdempotencyKeyFactory {

  private RewardIdempotencyKeyFactory() {}

  public static String forLevelUpReward(Long userId, Long levelUpHistoryId) {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (levelUpHistoryId == null || levelUpHistoryId <= 0) {
      throw new Web3InvalidInputException("levelUpHistoryId must be positive");
    }
    return "reward:" + userId + ":" + levelUpHistoryId;
  }
}
