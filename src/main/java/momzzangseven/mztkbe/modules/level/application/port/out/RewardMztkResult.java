package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record RewardMztkResult(Web3TxStatus status, String txHash, String failureReason) {

  public RewardMztkResult {
    if (status == null) {
      throw new LevelUpCommandInvalidException("Reward status must not be null");
    }
  }

  public static RewardMztkResult created(String reason) {
    return new RewardMztkResult(Web3TxStatus.CREATED, null, reason);
  }

  public static RewardMztkResult pending(String txHash) {
    return new RewardMztkResult(Web3TxStatus.PENDING, txHash, null);
  }

  public static RewardMztkResult success(String txHash) {
    return new RewardMztkResult(Web3TxStatus.SUCCEEDED, txHash, null);
  }

  public static RewardMztkResult unconfirmed(String reason, String txHash) {
    return new RewardMztkResult(Web3TxStatus.UNCONFIRMED, txHash, reason);
  }

  public void validate() {
    // no-op for backward compatibility; constructor enforces validity.
  }
}
