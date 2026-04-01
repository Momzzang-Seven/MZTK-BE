package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import java.util.Optional;

/**
 * Input port for retrieving the active wallet address of a user. Returns the address of the user's
 * currently ACTIVE wallet, or empty if none is registered.
 */
public interface GetActiveWalletAddressUseCase {

  /**
   * Returns the ACTIVE wallet address for the given user.
   *
   * @param userId the user's ID
   * @return the wallet address, or {@link Optional#empty()} if the user has no active wallet
   */
  Optional<String> execute(Long userId);
}
