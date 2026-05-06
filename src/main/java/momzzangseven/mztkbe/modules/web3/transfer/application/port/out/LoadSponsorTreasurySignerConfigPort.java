package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadSponsorTreasurySignerConfigPort {

  SponsorTreasurySignerConfig load();

  record SponsorTreasurySignerConfig(String walletAlias) {

    public SponsorTreasurySignerConfig {
      if (walletAlias == null || walletAlias.isBlank()) {
        throw new Web3InvalidInputException("sponsor walletAlias is required");
      }
    }
  }
}
