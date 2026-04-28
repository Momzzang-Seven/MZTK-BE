package momzzangseven.mztkbe.modules.web3.treasury.domain.model;

/**
 * Functional role a {@link TreasuryWallet} fulfils on-chain.
 *
 * <p>Each role maps to a single canonical wallet alias; downstream lookup ports ({@code
 * LoadRewardTreasuryAliasPort}, {@code LoadSponsorTreasuryAliasPort}) resolve a request to the
 * wallet bound to the alias produced by {@link #toAlias()}.
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
