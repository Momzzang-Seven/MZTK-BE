package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ProvisionTreasuryKeyAdminCommand(Long operatorId, String treasuryPrivateKey) {

  public void validate() {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    if (treasuryPrivateKey == null || treasuryPrivateKey.isBlank()) {
      throw new Web3InvalidInputException("treasuryPrivateKey is required");
    }
  }
}
