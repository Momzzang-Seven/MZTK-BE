package momzzangseven.mztkbe.global.error;

public class RewardFailedOnchainException extends BusinessException {

  public RewardFailedOnchainException(String referenceType, String referenceId) {
    super(
        ErrorCode.REWARD_FAILED_ONCHAIN,
        "Reward already FAILED_ONCHAIN. referenceType="
            + referenceType
            + ", referenceId="
            + referenceId);
  }
}
