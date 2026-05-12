package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Persists a new wallet registration session and exposes DB duplicate races early. */
public interface CreateWalletRegistrationSessionPort {

  /**
   * Creates and flushes a session so DB duplicate races are exposed before downstream side effects.
   *
   * <p>Implementations must participate in the caller's registration transaction so a later
   * approval-submit failure rolls back both the pending session and challenge consumption. If the
   * DB rejects the row because a non-terminal session already exists, callers must let the
   * transaction roll back and then reload the winning session through a fresh retry/read boundary.
   */
  WalletRegistrationSession createAndFlush(WalletRegistrationSession session);
}
