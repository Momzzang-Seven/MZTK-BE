package momzzangseven.mztkbe.modules.web3.treasury.domain.model;

/**
 * Lifecycle status of a {@link TreasuryWallet}.
 *
 * <p>Transition contract:
 *
 * <ul>
 *   <li>{@link #ACTIVE} → {@link #DISABLED} via {@code disable(Clock)} when an operator retires the
 *       wallet from new signing flows.
 *   <li>{@link #DISABLED} → {@link #ARCHIVED} via {@code archive(Clock)} once outstanding
 *       settlements have drained and the wallet is permanently retired.
 * </ul>
 *
 * <p>Only {@link #ACTIVE} wallets may produce signatures (enforced by {@code assertSignable}).
 */
public enum TreasuryWalletStatus {
  ACTIVE,
  DISABLED,
  ARCHIVED
}
