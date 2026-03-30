package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that returns the ACTIVE wallet address for a given user. If the user has no
 * active wallet, returns {@link Optional#empty()}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetActiveWalletAddressService implements GetActiveWalletAddressUseCase {

  private final LoadWalletPort loadWalletPort;

  /**
   * Looks up the user's ACTIVE wallet and returns its address.
   *
   * @param userId the user's ID
   * @return the active wallet address, or empty if none exists
   */
  @Override
  public Optional<String> execute(Long userId) {
    List<UserWallet> wallets =
        loadWalletPort.findWalletsByUserIdAndStatus(userId, WalletStatus.ACTIVE);
    return wallets.stream().map(UserWallet::getWalletAddress).findFirst();
  }
}
