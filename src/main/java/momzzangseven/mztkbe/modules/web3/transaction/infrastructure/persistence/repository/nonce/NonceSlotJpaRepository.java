package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface NonceSlotJpaRepository extends JpaRepository<NonceSlotEntity, NonceSlotId> {

  @Query(
      """
      select s
      from NonceSlotEntity s
      where s.chainId = :chainId
        and s.fromAddress = :fromAddress
        and s.status in :statuses
      order by s.nonce asc
      """)
  List<NonceSlotEntity> findByScopeAndStatusInOrderByNonce(
      @Param("chainId") Long chainId,
      @Param("fromAddress") String fromAddress,
      @Param("statuses") Collection<SponsorNonceSlotStatus> statuses,
      Pageable pageable);

  @Query(
      """
      select s
      from NonceSlotEntity s
      where s.chainId = :chainId
        and s.fromAddress = :fromAddress
      order by s.nonce asc
      """)
  List<NonceSlotEntity> findByScopeOrderByNonce(
      @Param("chainId") Long chainId, @Param("fromAddress") String fromAddress);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s
      from NonceSlotEntity s
      where s.chainId = :chainId
        and s.fromAddress = :fromAddress
        and s.nonce = :nonce
      """)
  Optional<NonceSlotEntity> findByScopeForUpdate(
      @Param("chainId") Long chainId,
      @Param("fromAddress") String fromAddress,
      @Param("nonce") Long nonce);
}
