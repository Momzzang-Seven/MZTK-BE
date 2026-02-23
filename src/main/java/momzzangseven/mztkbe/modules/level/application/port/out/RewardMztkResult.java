package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.LevelValidationMessage;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;

public record RewardMztkResult(RewardTxStatus status, String txHash, String failureReason) {

  public RewardMztkResult {
    if (status == null) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.REWARD_STATUS_REQUIRED);
    }
  }

  public static RewardMztkResult created(String reason) {
    return new RewardMztkResult(RewardTxStatus.CREATED, null, reason);
  }

  public static RewardMztkResult pending(String txHash) {
    return new RewardMztkResult(RewardTxStatus.PENDING, txHash, null);
  }

  public static RewardMztkResult success(String txHash) {
    return new RewardMztkResult(RewardTxStatus.SUCCEEDED, txHash, null);
  }

  public static RewardMztkResult unconfirmed(String reason, String txHash) {
    return new RewardMztkResult(RewardTxStatus.UNCONFIRMED, txHash, reason);
  }
}
