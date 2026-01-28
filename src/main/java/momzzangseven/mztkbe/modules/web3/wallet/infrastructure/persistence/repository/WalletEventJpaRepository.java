package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.WalletEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** WalletEvent JPA Repository */
public interface WalletEventJpaRepository extends JpaRepository<WalletEventEntity, Long> {

  /** Find events by wallet address (ordered by occurred_at DESC) */
  @Query(
      """
      SELECT e FROM WalletEventEntity e
      WHERE e.walletAddress = :address
      ORDER BY e.occurredAt DESC
      """)
  List<WalletEventEntity> findByWalletAddress(@Param("address") String address);

  /** Find events by wallet address (ordered by occurred_at ASC for E2E tests) */
  @Query(
      """
      SELECT e FROM WalletEventEntity e
      WHERE e.walletAddress = :address
      ORDER BY e.occurredAt ASC
      """)
  List<WalletEventEntity> findByWalletAddressOrderByOccurredAtAsc(@Param("address") String address);

  /** Find events by wallet address with pagination */
  @Query(
      """
      SELECT e FROM WalletEventEntity e
      WHERE e.walletAddress = :address
      ORDER BY e.occurredAt DESC
      """)
  Page<WalletEventEntity> findByWalletAddress(@Param("address") String address, Pageable pageable);

  /** Find events by user ID */
  @Query(
      """
      SELECT e FROM WalletEventEntity e
      WHERE e.userId = :userId
      ORDER BY e.occurredAt DESC
      """)
  List<WalletEventEntity> findByUserId(@Param("userId") Long userId);
}
