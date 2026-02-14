package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.LevelValidationMessage;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/** Command for issuing a level-up reward via {@code RewardMztkPort}. */
public record RewardMztkCommand(
    Long userId, int rewardMztk, Long referenceId, EvmAddress toWalletAddress) {

  public RewardMztkCommand {
    if (userId == null || userId <= 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.USER_ID_POSITIVE);
    }
    if (referenceId == null || referenceId <= 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.REFERENCE_ID_POSITIVE);
    }
    if (rewardMztk < 0) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.REWARD_MZTK_NON_NEGATIVE);
    }
    if (toWalletAddress == null) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.TO_WALLET_REQUIRED);
    }
  }
}
