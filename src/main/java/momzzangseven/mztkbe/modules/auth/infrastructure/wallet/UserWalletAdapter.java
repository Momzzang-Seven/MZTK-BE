package momzzangseven.mztkbe.modules.auth.infrastructure.wallet;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Component;

/**
 * Adapter that resolves a user's active wallet address by delegating to the wallet module's output
 * port.
 */
@Component
@RequiredArgsConstructor
public class UserWalletAdapter implements LoadUserWalletPort {

  private final LoadWalletPort loadWalletPort;

  @Override
  public Optional<String> findActiveWalletAddress(Long userId) {
    return loadWalletPort.findWalletsByUserIdAndStatus(userId, WalletStatus.ACTIVE).stream()
        .findFirst()
        .map(wallet -> wallet.getWalletAddress());
  }
}
