package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.UserWalletEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
  @Query("SELECT COUNT(w) FROM UserWalletEntity w WHERE w.userId = :userId AND w.status = :status")
  int countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") WalletStatus status);

  /** Find all active wallets by user ID */
  @Query("SELECT w FROM UserWalletEntity w WHERE w.userId = :userId AND w.status = :status")
  List<UserWalletEntity> findByUserIdAndStatus(
      @Param("userId") Long userId, @Param("status") WalletStatus status);

  /**
   * Load UNLINKED wallet information for scheduled hard deletion
   *
   * <p>Only retrieves UNLINKED wallets. USER_DELETED wallets are handled
   * by WithdrawalHardDeleteService as cascade deletion.
   *
   * @param cutoffDate cutoff date for deletion
   * @param pageable pagination info (for batch size limit)
   * @return list of UNLINKED wallet info (id, address, userId) to delete
   */
  @Query(
      """
      SELECT new momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort$WalletDeletionInfo(
          w.id, w.walletAddress, w.userId
      )
      FROM UserWalletEntity w
      WHERE w.status = momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus.UNLINKED
        AND w.unlinkedAt <= :cutoffDate
      ORDER BY w.unlinkedAt ASC, w.id ASC
      """)
  List<LoadWalletPort.WalletDeletionInfo> findWalletsForDeletion(
      @Param("cutoffDate") Instant cutoffDate, Pageable pageable);

  /**
   * Batch delete wallets by IDs
   *
   * @param ids wallet IDs to delete
   */
  @Modifying
  @Query("DELETE FROM UserWalletEntity w WHERE w.id IN :ids")
  void deleteAllByIdInBatch(@Param("ids") List<Long> ids);

  /**
   * Find wallet information by user IDs for hard delete cascade
   *
   * @param userIds user IDs
   * @return list of wallet info
   */
  @Query(
      """
      SELECT new momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort$WalletDeletionInfo(
          w.id, w.walletAddress, w.userId
      )
      FROM UserWalletEntity w
      WHERE w.userId IN :userIds
        AND w.status = momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus.USER_DELETED
      """)
  List<LoadWalletPort.WalletDeletionInfo> findWalletsByUserIdInAndUserDeleted(
      @Param("userIds") List<Long> userIds);
}
