package momzzangseven.mztkbe.global.error.level;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class RewardIntentCreationException extends BusinessException {

  public RewardIntentCreationException(Long userId, Long referenceId, Throwable cause) {
    super(
        ErrorCode.REWARD_INTENT_CREATION_FAILED,
        "Failed to create reward transaction intent: userId="
            + userId
            + ", referenceId="
            + referenceId,
        cause);
  }
}
