package momzzangseven.mztkbe.modules.web3.execution.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ExecutionSponsorWalletConfig(String walletAlias, String keyEncryptionKeyB64) {

  public ExecutionSponsorWalletConfig {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    if (keyEncryptionKeyB64 == null || keyEncryptionKeyB64.isBlank()) {
      throw new Web3InvalidInputException("keyEncryptionKeyB64 is required");
    }
  }
}
