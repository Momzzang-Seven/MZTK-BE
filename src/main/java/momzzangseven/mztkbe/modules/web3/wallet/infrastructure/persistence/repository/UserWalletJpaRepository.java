package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.UserWalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserWalletJpaRepository extends JpaRepository<UserWalletEntity, Long> {

  /** Find by wallet address */
  Optional<UserWalletEntity> findByWalletAddress(String walletAddress);

  /** Find by wallet address and status */
  Optional<UserWalletEntity> findByWalletAddressAndStatus(
      String walletAddress, WalletStatus status);

  /** Check if wallet exists by address */
  boolean existsByWalletAddress(String walletAddress);

  /** Check if wallet exists by address and status */
  boolean existsByWalletAddressAndStatus(String walletAddress, WalletStatus status);

  /** Count active wallets by user ID */
  @Query("SELECT COUNT(w) FROM UserWalletEntity w WHERE w.userId = :userId AND w.status = 'ACTIVE'")
  int countActiveWalletsByUserId(@Param("userId") Long userId);

  /** Find all active wallets by user ID */
  @Query("SELECT w FROM UserWalletEntity w WHERE w.userId = :userId AND w.status = 'ACTIVE'")
  List<UserWalletEntity> findActiveWalletsByUserId(@Param("userId") Long userId);
}
