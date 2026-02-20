package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import lombok.Builder;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.dto.ProvisionTreasuryKeyResult;

@Builder
public record ProvisionTreasuryKeyResponseDTO(String treasuryKeyEncryptionKeyB64) {

  public static ProvisionTreasuryKeyResponseDTO of(String treasuryKeyEncryptionKeyB64) {
    validate(treasuryKeyEncryptionKeyB64);
    return ProvisionTreasuryKeyResponseDTO.builder()
        .treasuryKeyEncryptionKeyB64(treasuryKeyEncryptionKeyB64)
        .build();
  }

  public static ProvisionTreasuryKeyResponseDTO from(ProvisionTreasuryKeyResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return ProvisionTreasuryKeyResponseDTO.of(result.treasuryKeyEncryptionKeyB64());
  }

  private static void validate(String treasuryKeyEncryptionKeyB64) {
    if (treasuryKeyEncryptionKeyB64 == null || treasuryKeyEncryptionKeyB64.isBlank()) {
      throw new Web3InvalidInputException("treasuryKeyEncryptionKeyB64 is required");
    }
  }
}
