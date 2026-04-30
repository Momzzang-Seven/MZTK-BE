package momzzangseven.mztkbe.modules.web3.shared.application.port.out;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadExecutionSignerConfigPort {

  ExecutionSignerConfig load();

  record ExecutionSignerConfig(String walletAlias, String keyEncryptionKeyB64) {

    public ExecutionSignerConfig {
      if (walletAlias == null || walletAlias.isBlank()) {
        throw new Web3InvalidInputException("walletAlias is required");
      }
      if (keyEncryptionKeyB64 == null || keyEncryptionKeyB64.isBlank()) {
        throw new Web3InvalidInputException("keyEncryptionKeyB64 is required");
      }
    }
  }
}
