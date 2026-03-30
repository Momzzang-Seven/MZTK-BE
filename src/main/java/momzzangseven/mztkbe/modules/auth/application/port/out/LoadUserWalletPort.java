package momzzangseven.mztkbe.modules.auth.application.port.out;

import java.util.Optional;

/** Port for looking up a user's active wallet address at login/reactivation time. */
public interface LoadUserWalletPort {

  /** Returns the active wallet address for the given user, or empty if none is registered. */
  Optional<String> findActiveWalletAddress(Long userId);
}
