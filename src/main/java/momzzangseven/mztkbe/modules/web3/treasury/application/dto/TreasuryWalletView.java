package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWalletStatus;

/**
 * Read-only projection of a {@link TreasuryWallet} surfaced across module boundaries.
 *
 * <p>External callers (admin controller, transaction / execution caller modules) consume this DTO
 * rather than the aggregate so that domain mutators ({@code disable}, {@code archive}) cannot be
 * invoked outside the owning service. {@link #role()} is recovered from the persisted alias via the
 * static factory below; an unknown alias falls back to {@code null} so that legacy rows (alias not
 * yet bound to any {@link TreasuryRole}) remain renderable.
 *
 * @param walletAlias canonical alias bound to the wallet
 * @param role functional role recovered from the alias, or {@code null} if unrecognised
 * @param kmsKeyId AWS KMS key id backing the wallet (may be {@code null} for legacy rows still
 *     awaiting backfill in PR4)
 * @param walletAddress {@code 0x}-prefixed Ethereum address recovered from the imported key
 * @param status lifecycle status
 * @param keyOrigin provenance of the underlying secret material
 * @param createdAt when the wallet row was first persisted
 * @param disabledAt when the wallet was disabled, or {@code null} while still ACTIVE
 */
public record TreasuryWalletView(
    String walletAlias,
    TreasuryRole role,
    String kmsKeyId,
    String walletAddress,
    TreasuryWalletStatus status,
    TreasuryKeyOrigin keyOrigin,
    LocalDateTime createdAt,
    LocalDateTime disabledAt) {

  /**
   * Build a view from a persisted aggregate, recovering {@link TreasuryRole} from the alias when
   * possible.
   */
  public static TreasuryWalletView from(TreasuryWallet wallet) {
    return new TreasuryWalletView(
        wallet.getWalletAlias(),
        roleFromAlias(wallet.getWalletAlias()),
        wallet.getKmsKeyId(),
        wallet.getWalletAddress(),
        wallet.getStatus(),
        wallet.getKeyOrigin(),
        wallet.getCreatedAt(),
        wallet.getDisabledAt());
  }

  private static TreasuryRole roleFromAlias(String walletAlias) {
    if (walletAlias == null) {
      return null;
    }
    for (TreasuryRole role : TreasuryRole.values()) {
      if (walletAlias.equals(role.toAlias())) {
        return role;
      }
    }
    return null;
  }
}
