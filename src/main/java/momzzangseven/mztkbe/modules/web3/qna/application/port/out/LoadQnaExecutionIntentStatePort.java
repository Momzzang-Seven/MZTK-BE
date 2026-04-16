package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

/** Loads the latest qna execution attempt state needed for conflict/recovery guards. */
public interface LoadQnaExecutionIntentStatePort {

  Optional<QnaExecutionIntentStateView> loadLatestByRootIdempotencyKey(String rootIdempotencyKey);

  Optional<QnaExecutionIntentStateView> loadLatestActiveByResource(
      QnaExecutionResourceType resourceType, String resourceId);

  default boolean hasConflictingActiveIntent(
      QnaExecutionResourceType resourceType,
      String resourceId,
      QnaExecutionActionType requestedActionType) {
    return loadLatestActiveByResource(resourceType, resourceId)
        .filter(intent -> !intent.matchesAction(requestedActionType))
        .isPresent();
  }
}
