package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Result of checking non-terminal wallet registration session duplicates. */
public record WalletRegistrationDuplicateResolution(
    WalletRegistrationDuplicateResolutionType type, WalletRegistrationSession session) {

  public static WalletRegistrationDuplicateResolution createNew() {
    return new WalletRegistrationDuplicateResolution(
        WalletRegistrationDuplicateResolutionType.CREATE_NEW, null);
  }

  public static WalletRegistrationDuplicateResolution reuse(WalletRegistrationSession session) {
    return new WalletRegistrationDuplicateResolution(
        WalletRegistrationDuplicateResolutionType.REUSE_EXISTING, session);
  }

  public static WalletRegistrationDuplicateResolution userConflict(
      WalletRegistrationSession session) {
    return new WalletRegistrationDuplicateResolution(
        WalletRegistrationDuplicateResolutionType.USER_HAS_PENDING_SESSION, session);
  }

  public static WalletRegistrationDuplicateResolution walletConflict(
      WalletRegistrationSession session) {
    return new WalletRegistrationDuplicateResolution(
        WalletRegistrationDuplicateResolutionType.WALLET_HAS_PENDING_SESSION, session);
  }

  public boolean shouldReuse() {
    return type == WalletRegistrationDuplicateResolutionType.REUSE_EXISTING;
  }

  public boolean isConflict() {
    return type == WalletRegistrationDuplicateResolutionType.USER_HAS_PENDING_SESSION
        || type == WalletRegistrationDuplicateResolutionType.WALLET_HAS_PENDING_SESSION;
  }
}
