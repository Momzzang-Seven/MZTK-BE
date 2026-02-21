package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Web3TransferPrepareJpaRepository
    extends JpaRepository<Web3TransferPrepareEntity, String> {

  Optional<Web3TransferPrepareEntity> findFirstByIdempotencyKeyOrderByCreatedAtDesc(
      String idempotencyKey);

  Optional<Web3TransferPrepareEntity> findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
      TokenTransferReferenceType referenceType, String referenceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Web3TransferPrepareEntity p where p.prepareId = :prepareId")
  Optional<Web3TransferPrepareEntity> findForUpdateByPrepareId(
      @Param("prepareId") String prepareId);

  @Query(
      "select p.prepareId from Web3TransferPrepareEntity p"
          + " where p.updatedAt < :cutoff order by p.updatedAt asc")
  List<String> findPrepareIdsForCleanup(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

  long deleteByPrepareIdIn(List<String> prepareIds);
}
