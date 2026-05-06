package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadRewardTreasurySignerConfigPort {

  RewardTreasurySignerConfig load();

  record RewardTreasurySignerConfig(String walletAlias) {

    public RewardTreasurySignerConfig {
      if (walletAlias == null || walletAlias.isBlank()) {
        throw new Web3InvalidInputException("reward walletAlias is required");
      }
    }
  }
}
