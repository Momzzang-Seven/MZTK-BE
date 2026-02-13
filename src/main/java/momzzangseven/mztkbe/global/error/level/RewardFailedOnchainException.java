package momzzangseven.mztkbe.global.error.level;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

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
