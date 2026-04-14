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

  @Query(
      "select e from Web3ExecutionIntentEntity e"
          + " where e.resourceType = :resourceType and e.resourceId = :resourceId"
          + " order by e.createdAt desc, e.id desc")
  List<Web3ExecutionIntentEntity> findLatestByResource(
      @Param("resourceType") ExecutionResourceType resourceType,
      @Param("resourceId") String resourceId,
      Pageable pageable);

  @Query(
      "select e from Web3ExecutionIntentEntity e"
          + " where e.resourceType = :resourceType and e.resourceId in :resourceIds"
          + " order by e.resourceId asc, e.createdAt desc, e.id desc")
  List<Web3ExecutionIntentEntity> findLatestByResources(
      @Param("resourceType") ExecutionResourceType resourceType,
      @Param("resourceIds") Collection<String> resourceIds);

  @Query(
      "select e from Web3ExecutionIntentEntity e"
          + " where e.requesterUserId = :requesterUserId"
          + " and e.resourceType = :resourceType and e.resourceId = :resourceId"
          + " order by e.createdAt desc, e.id desc")
  List<Web3ExecutionIntentEntity> findLatestByRequesterAndResource(
      @Param("requesterUserId") Long requesterUserId,
      @Param("resourceType") ExecutionResourceType resourceType,
      @Param("resourceId") String resourceId,
      Pageable pageable);

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

  @Query(
      "select e from Web3ExecutionIntentEntity e"
          + " where e.rootIdempotencyKey = :rootIdempotencyKey"
          + " order by e.attemptNo desc")
  List<Web3ExecutionIntentEntity> findAllByRootIdempotencyKey(
      @Param("rootIdempotencyKey") String rootIdempotencyKey, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from Web3ExecutionIntentEntity e"
          + " where e.resourceType = :resourceType and e.resourceId = :resourceId"
          + " and e.status in :statuses"
          + " order by e.createdAt desc, e.id desc")
  List<Web3ExecutionIntentEntity> findLatestByResourceAndStatusInForUpdate(
      @Param("resourceType") ExecutionResourceType resourceType,
      @Param("resourceId") String resourceId,
      @Param("statuses") Collection<ExecutionIntentStatus> statuses,
      Pageable pageable);

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
