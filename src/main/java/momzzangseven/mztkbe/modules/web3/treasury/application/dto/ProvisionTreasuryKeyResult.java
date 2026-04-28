package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ProvisionTreasuryKeyResult(
    String treasuryAddress,
    String treasuryPrivateKeyEncrypted,
    String treasuryKeyEncryptionKeyB64) {

  public static ProvisionTreasuryKeyResult of(
      String treasuryAddress,
      String treasuryPrivateKeyEncrypted,
      String treasuryKeyEncryptionKeyB64) {
    validate(treasuryAddress, treasuryPrivateKeyEncrypted, treasuryKeyEncryptionKeyB64);
    return new ProvisionTreasuryKeyResult(
        treasuryAddress, treasuryPrivateKeyEncrypted, treasuryKeyEncryptionKeyB64);
  }

  private static void validate(
      String treasuryAddress,
      String treasuryPrivateKeyEncrypted,
      String treasuryKeyEncryptionKeyB64) {
    if (treasuryAddress == null || treasuryAddress.isBlank()) {
      throw new Web3InvalidInputException("treasuryAddress is required");
    }
    if (treasuryPrivateKeyEncrypted == null || treasuryPrivateKeyEncrypted.isBlank()) {
      throw new Web3InvalidInputException("treasuryPrivateKeyEncrypted is required");
    }
    if (treasuryKeyEncryptionKeyB64 == null || treasuryKeyEncryptionKeyB64.isBlank()) {
      throw new Web3InvalidInputException("treasuryKeyEncryptionKeyB64 is required");
    }
  }
}
