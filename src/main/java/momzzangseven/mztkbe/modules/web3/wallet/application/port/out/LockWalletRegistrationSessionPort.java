package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Lock port for wallet registration state mutations. */
public interface LockWalletRegistrationSessionPort {

  /**
   * Loads a session with a pessimistic write lock.
   *
   * <p>Every wallet-registration session state mutation must enter through this method before
   * saving the changed aggregate. Mutation flows must not call execution mutation ports while
   * holding this lock; collect the required wallet-state decision first, release the wallet lock,
   * then call execution if needed to avoid cross-module lock-order deadlocks.
   */
  Optional<WalletRegistrationSession> lockByPublicIdForUpdate(String publicId);
}
