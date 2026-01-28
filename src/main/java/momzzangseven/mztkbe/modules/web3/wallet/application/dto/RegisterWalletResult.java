package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;

public record RegisterWalletResult(Long walletId, String walletAddress, Instant registeredAt) {
  public static RegisterWalletResult from(UserWallet wallet) {
    return new RegisterWalletResult(
        wallet.getId(), wallet.getWalletAddress(), wallet.getRegisteredAt());
  }
}
