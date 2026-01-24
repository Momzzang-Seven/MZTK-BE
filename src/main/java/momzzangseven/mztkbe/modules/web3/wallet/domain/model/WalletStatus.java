package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

/** Wallet status enum */
public enum WalletStatus {
  /** Active and usable */
  ACTIVE,

  /** Deactivated by user (soft delete) */
  INACTIVE,

  /** Blacklisted by admin */
  BLACKLISTED
}
