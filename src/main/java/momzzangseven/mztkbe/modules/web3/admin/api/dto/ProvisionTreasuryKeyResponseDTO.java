package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.dto.ProvisionTreasuryKeyResult;

@Builder
public record ProvisionTreasuryKeyResponseDTO(
    String treasuryAddress,
    String treasuryPrivateKeyEncrypted,
    String treasuryKeyEncryptionKeyB64) {

  public static ProvisionTreasuryKeyResponseDTO of(
      String treasuryAddress,
      String treasuryPrivateKeyEncrypted,
      String treasuryKeyEncryptionKeyB64) {
    validate(treasuryAddress, treasuryPrivateKeyEncrypted, treasuryKeyEncryptionKeyB64);
    return ProvisionTreasuryKeyResponseDTO.builder()
        .treasuryAddress(treasuryAddress)
        .treasuryPrivateKeyEncrypted(treasuryPrivateKeyEncrypted)
        .treasuryKeyEncryptionKeyB64(treasuryKeyEncryptionKeyB64)
        .build();
  }

  public static ProvisionTreasuryKeyResponseDTO from(ProvisionTreasuryKeyResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return ProvisionTreasuryKeyResponseDTO.of(
        result.treasuryAddress(),
        result.treasuryPrivateKeyEncrypted(),
        result.treasuryKeyEncryptionKeyB64());
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
