package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;

/** Port for loading wallet information */
public interface LoadWalletPort {
  /** Find wallet by ID */
  Optional<UserWallet> findById(Long walletId);

  /** Find wallet by address */
  Optional<UserWallet> findByWalletAddress(String walletAddress);

  /** Check if wallet exists by address */
  boolean existsByWalletAddress(String walletAddress);

  /** Get wallet status (returns empty if wallet doesn't exist) */
  Optional<WalletStatus> getWalletStatus(String walletAddress);

  /** Count active wallets by user ID */
  int countActiveWalletsByUserId(Long userId);

  /** Find active wallets by user ID */
  List<UserWallet> findActiveWalletsByUserId(Long userId);
}
