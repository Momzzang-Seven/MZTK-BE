package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadSponsorTreasurySignerConfigPort {

  SponsorTreasurySignerConfig load();

  record SponsorTreasurySignerConfig(String walletAlias, String keyEncryptionKeyB64) {

    public SponsorTreasurySignerConfig {
      if (walletAlias == null || walletAlias.isBlank()) {
        throw new Web3InvalidInputException("sponsor walletAlias is required");
      }
      if (keyEncryptionKeyB64 == null || keyEncryptionKeyB64.isBlank()) {
        throw new Web3InvalidInputException("sponsor keyEncryptionKeyB64 is required");
      }
    }
  }
}
