package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

/** Duplicate-session policy outcomes for wallet registration. */
public enum WalletRegistrationDuplicateResolutionType {
  CREATE_NEW,
  REUSE_EXISTING,
  USER_HAS_PENDING_SESSION,
  WALLET_HAS_PENDING_SESSION
}
