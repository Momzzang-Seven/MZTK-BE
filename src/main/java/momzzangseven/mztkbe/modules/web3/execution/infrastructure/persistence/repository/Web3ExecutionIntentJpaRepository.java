package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.entity.Web3ExecutionIntentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Web3ExecutionIntentJpaRepository
    extends JpaRepository<Web3ExecutionIntentEntity, Long> {

  Optional<Web3ExecutionIntentEntity> findByPublicId(String publicId);

  Optional<Web3ExecutionIntentEntity> findBySubmittedTxId(Long submittedTxId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Web3ExecutionIntentEntity e where e.submittedTxId = :submittedTxId")
  Optional<Web3ExecutionIntentEntity> findBySubmittedTxIdForUpdate(
      @Param("submittedTxId") Long submittedTxId);

  Optional<Web3ExecutionIntentEntity>
      findFirstByRequesterUserIdAndResourceTypeAndResourceIdOrderByAttemptNoDesc(
          Long requesterUserId, ExecutionResourceType resourceType, String resourceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Web3ExecutionIntentEntity e where e.publicId = :publicId")
  Optional<Web3ExecutionIntentEntity> findByPublicIdForUpdate(@Param("publicId") String publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from Web3ExecutionIntentEntity e"
          + " where e.rootIdempotencyKey = :rootIdempotencyKey"
          + " order by e.attemptNo desc")
  List<Web3ExecutionIntentEntity> findAllByRootIdempotencyKeyForUpdate(
      @Param("rootIdempotencyKey") String rootIdempotencyKey, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Web3ExecutionIntentEntity e where e.id in :ids")
  List<Web3ExecutionIntentEntity> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

  @Query(
      "select e.id from Web3ExecutionIntentEntity e"
          + " where e.status = :status and e.expiresAt < :now"
          + " order by e.expiresAt asc")
  List<Long> findIdsByStatusAndExpiresAtBefore(
      @Param("status") ExecutionIntentStatus status,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  @Query(
      "select e.id from Web3ExecutionIntentEntity e"
          + " where e.status in :statuses and e.updatedAt < :cutoff"
          + " order by e.updatedAt asc")
  List<Long> findIdsByStatusInAndUpdatedAtBefore(
      @Param("statuses") Collection<ExecutionIntentStatus> statuses,
      @Param("cutoff") LocalDateTime cutoff,
      Pageable pageable);

  long deleteByIdIn(Collection<Long> ids);
}
