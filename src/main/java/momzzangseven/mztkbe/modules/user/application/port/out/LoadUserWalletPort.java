package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Optional;

/**
 * Output port for loading a user's active wallet address. Implemented by an infrastructure adapter
 * that delegates to the wallet module.
 */
public interface LoadUserWalletPort {

  /**
   * Returns the active wallet address registered by the given user.
   *
   * @param userId the user's ID
   * @return the active wallet address, or {@link Optional#empty()} if none is registered
   */
  Optional<String> loadActiveWalletAddress(Long userId);
}
