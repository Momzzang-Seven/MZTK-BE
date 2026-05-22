package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.WalletRegistrationSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRegistrationSessionJpaRepository
    extends JpaRepository<WalletRegistrationSessionEntity, Long> {

  Optional<WalletRegistrationSessionEntity> findByPublicId(String publicId);

  Optional<WalletRegistrationSessionEntity> findByPublicIdAndUserId(String publicId, Long userId);

  Optional<WalletRegistrationSessionEntity> findByLatestExecutionIntentId(
      String latestExecutionIntentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from WalletRegistrationSessionEntity s where s.publicId = :publicId")
  Optional<WalletRegistrationSessionEntity> findByPublicIdForUpdate(
      @Param("publicId") String publicId);

  @Query(
      "select s from WalletRegistrationSessionEntity s"
          + " where s.userId = :userId and s.status in :statuses"
          + " order by s.createdAt desc, s.id desc")
  List<WalletRegistrationSessionEntity> findLatestByUserIdAndStatusIn(
      @Param("userId") Long userId,
      @Param("statuses") Collection<WalletRegistrationStatus> statuses,
      Pageable pageable);

  @Query(
      "select s from WalletRegistrationSessionEntity s"
          + " where s.walletAddress = :walletAddress and s.status in :statuses"
          + " order by s.createdAt desc, s.id desc")
  List<WalletRegistrationSessionEntity> findLatestByWalletAddressAndStatusIn(
      @Param("walletAddress") String walletAddress,
      @Param("statuses") Collection<WalletRegistrationStatus> statuses,
      Pageable pageable);

  @Query(
      "select s from WalletRegistrationSessionEntity s"
          + " where s.userId = :userId and s.walletAddress = :walletAddress"
          + " and s.status in :statuses"
          + " order by s.createdAt desc, s.id desc")
  List<WalletRegistrationSessionEntity> findLatestByUserIdAndWalletAddressAndStatusIn(
      @Param("userId") Long userId,
      @Param("walletAddress") String walletAddress,
      @Param("statuses") Collection<WalletRegistrationStatus> statuses,
      Pageable pageable);

  List<WalletRegistrationSessionEntity> findByStatusInOrderByUpdatedAtAscIdAsc(
      Collection<WalletRegistrationStatus> statuses, Pageable pageable);

  @Query(
      """
      select count(s) > 0
      from WalletRegistrationSessionEntity s
      where (s.userId = :userId or s.walletAddress = :walletAddress)
        and s.status in :statuses
        and (
          s.createdAt > :createdAt
          or (s.createdAt = :createdAt and s.id > :sessionId)
        )
      """)
  boolean existsNewerByUserIdOrWalletAddress(
      @Param("userId") Long userId,
      @Param("walletAddress") String walletAddress,
      @Param("statuses") Collection<WalletRegistrationStatus> statuses,
      @Param("createdAt") LocalDateTime createdAt,
      @Param("sessionId") Long sessionId);
}
