package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;

public interface ExecutionIntentPersistencePort {

  Optional<ExecutionIntent> findByPublicId(String publicId);

  Optional<ExecutionIntent> findByPublicIdForUpdate(String publicId);

  Optional<ExecutionIntent> findLatestByResource(
      ExecutionResourceType resourceType, String resourceId);

  List<ExecutionIntent> findByResource(
      ExecutionResourceType resourceType, String resourceId, int limit);

  default Map<String, ExecutionIntent> findLatestByResources(
      ExecutionResourceType resourceType, Collection<String> resourceIds) {
    Map<String, ExecutionIntent> results = new LinkedHashMap<>();
    for (String resourceId : resourceIds) {
      findLatestByResource(resourceType, resourceId)
          .ifPresent(intent -> results.put(resourceId, intent));
    }
    return results;
  }

  Optional<ExecutionIntent> findLatestByRequesterAndResource(
      Long requesterUserId, ExecutionResourceType resourceType, String resourceId);

  Optional<ExecutionIntent> findLatestByRootIdempotencyKeyForUpdate(String rootIdempotencyKey);

  Optional<ExecutionIntent> findLatestByRootIdempotencyKey(String rootIdempotencyKey);

  Optional<ExecutionIntent> findLatestActiveByResource(
      ExecutionResourceType resourceType, String resourceId);

  List<ExecutionIntent> findActiveByResource(ExecutionResourceType resourceType, String resourceId);

  Optional<ExecutionIntent> findLatestActiveByResourceForUpdate(
      ExecutionResourceType resourceType, String resourceId);

  List<ExecutionIntent> findActiveByResourceForUpdate(
      ExecutionResourceType resourceType, String resourceId);

  boolean existsActiveByResourceAndActionTypeNotForUpdate(
      ExecutionResourceType resourceType, String resourceId, ExecutionActionType actionType);

  Optional<ExecutionIntent> claimNextInternalExecutableForUpdate(
      List<ExecutionActionType> actionTypes);

  /**
   * Non-locking peek that returns {@code true} iff at least one row matches the same filter as
   * {@link #claimNextInternalExecutableForUpdate(List)}. Lets the cron orchestrator skip sponsor
   * preflight (and the terminal-KMS log line it can produce) when the queue is empty, so
   * KMS-DescribeKey outages do not produce one ERROR log per scheduler tick.
   */
  boolean existsClaimableInternal(List<ExecutionActionType> actionTypes);

  Optional<ExecutionIntent> findBySubmittedTxId(Long submittedTxId);

  Optional<ExecutionIntent> findBySubmittedTxIdForUpdate(Long submittedTxId);

  ExecutionIntent create(ExecutionIntent executionIntent);

  ExecutionIntent update(ExecutionIntent executionIntent);

  List<Long> findExpiredAwaitingSignatureIds(LocalDateTime now, int batchSize);

  List<Long> findRetainedFinalizedIds(LocalDateTime cutoff, int batchSize);

  List<ExecutionIntent> findAllByIdsForUpdate(Collection<Long> ids);

  long deleteByIds(Collection<Long> ids);
}
