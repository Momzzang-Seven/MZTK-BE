package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.modules.web3.domain.vo.EvmAddress;

/** Command for issuing a level-up reward via {@code RewardMztkPort}. */
public record RewardMztkCommand(
    Long userId, int rewardMztk, Long referenceId, EvmAddress toWalletAddress) {

  public RewardMztkCommand {
    if (userId == null || userId <= 0) {
      throw new LevelUpCommandInvalidException("userId must be positive");
    }
    if (referenceId == null || referenceId <= 0) {
      throw new LevelUpCommandInvalidException("referenceId must be positive");
    }
    if (rewardMztk < 0) {
      throw new LevelUpCommandInvalidException("rewardMztk must be >= 0");
    }
    if (toWalletAddress == null) {
      throw new LevelUpCommandInvalidException("toWalletAddress is required");
    }
  }
}
