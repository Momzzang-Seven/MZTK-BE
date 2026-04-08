package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;

public interface ExecutionIntentPersistencePort {

  Optional<ExecutionIntent> findByPublicId(String publicId);

  Optional<ExecutionIntent> findByPublicIdForUpdate(String publicId);

  Optional<ExecutionIntent> findLatestByRequesterAndResource(
      Long requesterUserId, ExecutionResourceType resourceType, String resourceId);

  Optional<ExecutionIntent> findLatestByRootIdempotencyKeyForUpdate(String rootIdempotencyKey);

  Optional<ExecutionIntent> findBySubmittedTxId(Long submittedTxId);

  Optional<ExecutionIntent> findBySubmittedTxIdForUpdate(Long submittedTxId);

  ExecutionIntent create(ExecutionIntent executionIntent);

  ExecutionIntent update(ExecutionIntent executionIntent);

  List<Long> findExpiredAwaitingSignatureIds(LocalDateTime now, int batchSize);

  List<Long> findRetainedFinalizedIds(LocalDateTime cutoff, int batchSize);

  List<ExecutionIntent> findAllByIdsForUpdate(Collection<Long> ids);

  long deleteByIds(Collection<Long> ids);
}
