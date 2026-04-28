package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWalletStatus;

public record ProvisionTreasuryKeyResponseDTO(
    String walletAlias,
    TreasuryRole role,
    String kmsKeyId,
    String walletAddress,
    TreasuryWalletStatus status,
    TreasuryKeyOrigin keyOrigin,
    LocalDateTime createdAt) {

  public static ProvisionTreasuryKeyResponseDTO from(ProvisionTreasuryKeyResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return new ProvisionTreasuryKeyResponseDTO(
        result.walletAlias(),
        result.role(),
        result.kmsKeyId(),
        result.walletAddress(),
        result.status(),
        result.keyOrigin(),
        result.createdAt());
  }
}
