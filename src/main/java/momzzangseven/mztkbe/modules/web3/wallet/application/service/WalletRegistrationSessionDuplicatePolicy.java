package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolution;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.springframework.stereotype.Component;

/** Resolves duplicate non-terminal wallet registration sessions before creating a new session. */
@Component
public class WalletRegistrationSessionDuplicatePolicy {

  /**
   * Resolves duplicate policy from already-loaded candidates.
   *
   * <p>Exact user + wallet match is reusable. Same user with another wallet, or same wallet with
   * another user, must be treated as a conflict so the caller does not create another approval
   * intent and waste gas.
   */
  public WalletRegistrationDuplicateResolution resolve(
      Long userId,
      String walletAddress,
      Optional<WalletRegistrationSession> exact,
      Optional<WalletRegistrationSession> byUser,
      Optional<WalletRegistrationSession> byWallet) {
    if (exact.isPresent()) {
      return WalletRegistrationDuplicateResolution.reuse(exact.get());
    }
    if (byUser.isPresent()) {
      return WalletRegistrationDuplicateResolution.userConflict(byUser.get());
    }
    if (byWallet.isPresent()) {
      return WalletRegistrationDuplicateResolution.walletConflict(byWallet.get());
    }
    return WalletRegistrationDuplicateResolution.createNew();
  }
}
