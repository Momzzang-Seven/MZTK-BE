package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ProvisionTreasuryKeyCommand(
    Long operatorId, String treasuryPrivateKey, String walletAlias) {

  public void validate() {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    if (treasuryPrivateKey == null || treasuryPrivateKey.isBlank()) {
      throw new Web3InvalidInputException("treasuryPrivateKey is required");
    }
    if (walletAlias != null && walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias must not be blank");
    }
  }
}
