package momzzangseven.mztkbe.modules.user.infrastructure.external.web3.wallet.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.stereotype.Component;

/**
 * Driven adapter that bridges the user module's {@link LoadUserWalletPort} to the wallet module's
 * {@link GetActiveWalletAddressUseCase}. Fetches the active wallet address from the
 * {@code user_wallets} table via the wallet module, keeping the user application layer free of
 * direct wallet module dependencies.
 */
@Component
@RequiredArgsConstructor
public class UserWalletAdapter implements LoadUserWalletPort {

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
