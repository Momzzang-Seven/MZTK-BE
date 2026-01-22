package momzzangseven.mztkbe.modules.level.application.port.out;

import lombok.Builder;
import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;

@Builder
public record RewardMztkResult(RewardStatus status, String txHash, String failureReason) {

  public static RewardMztkResult pending(String reason) {
    return RewardMztkResult.builder().status(RewardStatus.PENDING).failureReason(reason).build();
  }

  public static RewardMztkResult success(String txHash) {
    return RewardMztkResult.builder().status(RewardStatus.SUCCESS).txHash(txHash).build();
  }

  public static RewardMztkResult failed(String reason) {
    return RewardMztkResult.builder().status(RewardStatus.FAILED).failureReason(reason).build();
  }

  public void validate() {
    if (status == null) {
      throw new IllegalStateException("Reward status must not be null");
    }
  }
}
