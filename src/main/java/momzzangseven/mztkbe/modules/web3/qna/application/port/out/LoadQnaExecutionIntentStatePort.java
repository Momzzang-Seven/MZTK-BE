package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

/** Loads the latest qna execution attempt state needed for conflict/recovery guards. */
public interface LoadQnaExecutionIntentStatePort {

  Optional<QnaExecutionIntentStateView> loadLatestByRootIdempotencyKey(String rootIdempotencyKey);

  Optional<QnaExecutionIntentStateView> loadLatestActiveByResource(
      QnaExecutionResourceType resourceType, String resourceId);

  List<QnaExecutionIntentStateView> loadActiveByResource(
      QnaExecutionResourceType resourceType, String resourceId);

  Optional<QnaExecutionIntentStateView> loadLatestActiveByResourceForUpdate(
      QnaExecutionResourceType resourceType, String resourceId);

  List<QnaExecutionIntentStateView> loadActiveByResourceForUpdate(
      QnaExecutionResourceType resourceType, String resourceId);

  default Map<String, QnaExecutionIntentStateView> loadLatestActiveByResources(
      QnaExecutionResourceType resourceType, Collection<String> resourceIds) {
    Map<String, QnaExecutionIntentStateView> latestByResourceId = new LinkedHashMap<>();
    if (resourceIds == null || resourceIds.isEmpty()) {
      return latestByResourceId;
    }
    for (String resourceId : resourceIds) {
      loadLatestActiveByResource(resourceType, resourceId)
          .ifPresent(intent -> latestByResourceId.put(resourceId, intent));
    }
    return latestByResourceId;
  }

  default Map<String, QnaExecutionIntentStateView> loadLatestActiveByResourcesForUpdate(
      QnaExecutionResourceType resourceType, Collection<String> resourceIds) {
    Map<String, QnaExecutionIntentStateView> latestByResourceId = new LinkedHashMap<>();
    if (resourceIds == null || resourceIds.isEmpty()) {
      return latestByResourceId;
    }
    for (String resourceId : resourceIds) {
      loadLatestActiveByResourceForUpdate(resourceType, resourceId)
          .ifPresent(intent -> latestByResourceId.put(resourceId, intent));
    }
    return latestByResourceId;
  }

  default boolean hasConflictingActiveIntent(
      QnaExecutionResourceType resourceType,
      String resourceId,
      QnaExecutionActionType requestedActionType) {
    return loadActiveByResourceForUpdate(resourceType, resourceId).stream()
        .anyMatch(intent -> !intent.matchesAction(requestedActionType));
  }

  default boolean hasActiveIntentForUpdate(
      QnaExecutionResourceType resourceType, String resourceId) {
    return !loadActiveByResourceForUpdate(resourceType, resourceId).isEmpty();
  }
}
