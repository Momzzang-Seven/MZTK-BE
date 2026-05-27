package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.SponsorNonceLockEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.SponsorNonceLockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SponsorNonceLockJpaRepository
    extends JpaRepository<SponsorNonceLockEntity, SponsorNonceLockId> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select l
      from SponsorNonceLockEntity l
      where l.chainId = :chainId
        and l.fromAddress = :fromAddress
      """)
  Optional<SponsorNonceLockEntity> findByScopeForUpdate(
      @Param("chainId") Long chainId, @Param("fromAddress") String fromAddress);
}
