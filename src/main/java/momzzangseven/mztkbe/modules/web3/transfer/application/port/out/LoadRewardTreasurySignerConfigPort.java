package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadRewardTreasurySignerConfigPort {

  RewardTreasurySignerConfig load();

  record RewardTreasurySignerConfig(String walletAlias, String keyEncryptionKeyB64) {

    public RewardTreasurySignerConfig {
      if (walletAlias == null || walletAlias.isBlank()) {
        throw new Web3InvalidInputException("reward walletAlias is required");
      }
      if (keyEncryptionKeyB64 == null || keyEncryptionKeyB64.isBlank()) {
        throw new Web3InvalidInputException("reward keyEncryptionKeyB64 is required");
      }
    }
  }
}
