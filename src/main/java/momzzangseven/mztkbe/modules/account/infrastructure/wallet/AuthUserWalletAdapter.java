package momzzangseven.mztkbe.modules.account.infrastructure.wallet;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.stereotype.Component;

/**
 * Adapter that resolves a user's active wallet address by delegating to the wallet module's output
 * port.
 */
@Component
@RequiredArgsConstructor
public class AuthUserWalletAdapter implements LoadUserWalletPort {

  private final GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;

  /**
   * Returns the active wallet address for the given user by delegating to the wallet module.
   *
   * @param userId the user's ID
   * @return the active wallet address, or empty if none is registered
   */
  @Override
  public Optional<String> loadActiveWalletAddress(Long userId) {
    return getActiveWalletAddressUseCase.execute(userId);
  }
}
