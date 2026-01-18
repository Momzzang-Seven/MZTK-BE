package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.domain.model.RewardStatus;

public interface UpdateLevelUpHistoryRewardPort {
  void updateReward(Long levelUpHistoryId, RewardStatus status, String txHash);
}
