package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWalletStatus;

/**
 * Result of a successful KMS-backed provisioning. Mirrors {@link TreasuryWalletView} except it
 * always carries a non-null {@code kmsKeyId} and {@code role} since both are produced by the
 * service.
 */
public record ProvisionTreasuryKeyResult(
    String walletAlias,
    TreasuryRole role,
    String kmsKeyId,
    String walletAddress,
    TreasuryWalletStatus status,
    TreasuryKeyOrigin keyOrigin,
    LocalDateTime createdAt) {

  public static ProvisionTreasuryKeyResult from(TreasuryWallet wallet, TreasuryRole role) {
    return new ProvisionTreasuryKeyResult(
        wallet.getWalletAlias(),
        role,
        wallet.getKmsKeyId(),
        wallet.getWalletAddress(),
        wallet.getStatus(),
        wallet.getKeyOrigin(),
        wallet.getCreatedAt());
  }
}
