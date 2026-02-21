package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ProvisionTreasuryKeyResult(String treasuryKeyEncryptionKeyB64) {

  public ProvisionTreasuryKeyResult {
    if (treasuryKeyEncryptionKeyB64 == null || treasuryKeyEncryptionKeyB64.isBlank()) {
      throw new Web3InvalidInputException("treasuryKeyEncryptionKeyB64 is required");
    }
  }
}
