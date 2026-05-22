package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

/** Transaction-scoped authority lock for wallet registration user/wallet decisions. */
public interface AcquireWalletRegistrationAuthorityLockPort {

  /**
   * Acquires deterministic authority locks for a user and wallet address.
   *
   * <p>Callers must acquire this before checking active-wallet eligibility, resolving duplicate
   * registration sessions, creating new sessions, or finalizing a confirmed registration.
   */
  void lock(Long userId, String walletAddress);
}
