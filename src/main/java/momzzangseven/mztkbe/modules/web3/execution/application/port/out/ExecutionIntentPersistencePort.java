package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;

public interface ExecutionIntentPersistencePort {

  Optional<ExecutionIntent> findByPublicId(String publicId);

  Optional<ExecutionIntent> findByPublicIdForUpdate(String publicId);

  Optional<ExecutionIntent> findLatestByResource(
      ExecutionResourceType resourceType, String resourceId);

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

  Optional<ExecutionIntent> findLatestActiveByResourceForUpdate(
      ExecutionResourceType resourceType, String resourceId);

  Optional<ExecutionIntent> findBySubmittedTxId(Long submittedTxId);

  Optional<ExecutionIntent> findBySubmittedTxIdForUpdate(Long submittedTxId);

  ExecutionIntent create(ExecutionIntent executionIntent);

  ExecutionIntent update(ExecutionIntent executionIntent);

  List<Long> findExpiredAwaitingSignatureIds(LocalDateTime now, int batchSize);

  List<Long> findRetainedFinalizedIds(LocalDateTime cutoff, int batchSize);

  List<ExecutionIntent> findAllByIdsForUpdate(Collection<Long> ids);

  long deleteByIds(Collection<Long> ids);
}
