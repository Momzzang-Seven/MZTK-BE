package momzzangseven.mztkbe.modules.level.application.port.out;

import lombok.Builder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

@Builder(toBuilder = true)
public record RewardMztkResult(Web3TxStatus status, String txHash, String failureReason) {

  public static RewardMztkResult created(String reason) {
    return RewardMztkResult.builder().status(Web3TxStatus.CREATED).failureReason(reason).build();
  }

  public static RewardMztkResult pending(String txHash) {
    return RewardMztkResult.builder().status(Web3TxStatus.PENDING).txHash(txHash).build();
  }

  public static RewardMztkResult success(String txHash) {
    return RewardMztkResult.builder().status(Web3TxStatus.SUCCEEDED).txHash(txHash).build();
  }

  public static RewardMztkResult unconfirmed(String reason, String txHash) {
    return RewardMztkResult.builder()
        .status(Web3TxStatus.UNCONFIRMED)
        .failureReason(reason)
        .txHash(txHash)
        .build();
  }

  public void validate() {
    if (status == null) {
      throw new IllegalStateException("Reward status must not be null");
    }
  }
}
