package momzzangseven.mztkbe.modules.web3.treasury.domain.vo;

/**
 * Functional role a {@link TreasuryWallet} fulfils on-chain.
 *
 * <p>Each role maps to a single canonical wallet alias via {@link #toAlias()}. Callers use the
 * alias as a lookup key against {@code web3_treasury_wallets.wallet_alias} (single source of truth
 * for the actual alias value); the enum-side mapping is the code-DB contract — provision-time
 * domain invariants enforce that the row's stored alias equals {@code role.toAlias()}.
 */
public enum TreasuryRole {
  REWARD,
  SPONSOR;

  private static final String REWARD_ALIAS = "reward-treasury";
  private static final String SPONSOR_ALIAS = "sponsor-treasury";

  /**
   * @return the canonical wallet alias bound to this role
   */
  public String toAlias() {
    return switch (this) {
      case REWARD -> REWARD_ALIAS;
      case SPONSOR -> SPONSOR_ALIAS;
    };
  }
}
