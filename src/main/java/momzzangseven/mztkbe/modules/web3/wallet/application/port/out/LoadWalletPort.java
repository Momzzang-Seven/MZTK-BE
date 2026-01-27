package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.time.Instant;
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

  /** Find wallet by address and status */
  Optional<UserWallet> findByWalletAddressAndStatus(
      String walletAddress, WalletStatus walletStatus);

  /** Check if wallet exists by address */
  boolean existsByWalletAddress(String walletAddress);

  /** Check if wallet exists by address and status */
  boolean existsByWalletAddressAndStatus(String walletAddress, WalletStatus walletStatus);

  /** Get wallet status (returns empty if wallet doesn't exist) */
  Optional<WalletStatus> getWalletStatus(String walletAddress);

  /** Count active wallets by user ID */
  int countWalletsByUserIdAndStatus(Long userId, WalletStatus status);

  /** Find active wallets by user ID */
  List<UserWallet> findWalletsByUserIdAndStatus(Long userId, WalletStatus status);

  List<WalletDeletionInfo> loadWalletsForDeletion(Instant cutoffDate, int limit);

  List<WalletDeletionInfo> findWalletsByUserIdInAndUserDeleted(List<Long> userIds);

  /**
   * Wallet deletion info record
   *
   * @param id wallet ID
   * @param walletAddress wallet address
   * @param userId user ID
   */
  record WalletDeletionInfo(Long id, String walletAddress, Long userId) {}
}
