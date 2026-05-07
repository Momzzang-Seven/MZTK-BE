package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;

/** Bridges shared execution intent persistence into qna-owned conflict/recovery state checks. */
@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class QnaExecutionIntentStateAdapter implements LoadQnaExecutionIntentStatePort {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;

  @Override
  public Optional<QnaExecutionIntentStateView> loadLatestByRootIdempotencyKey(
      String rootIdempotencyKey) {
    return executionIntentPersistencePort
        .findLatestByRootIdempotencyKey(rootIdempotencyKey)
        .map(this::toView);
  }

  @Override
  public Optional<QnaExecutionIntentStateView> loadLatestActiveByResource(
      QnaExecutionResourceType resourceType, String resourceId) {
    return executionIntentPersistencePort
        .findLatestActiveByResource(toExecutionResourceType(resourceType), resourceId)
        .map(this::toView);
  }

  @Override
  public List<QnaExecutionIntentStateView> loadActiveByResource(
      QnaExecutionResourceType resourceType, String resourceId) {
    return executionIntentPersistencePort
        .findActiveByResource(toExecutionResourceType(resourceType), resourceId)
        .stream()
        .map(this::toView)
        .toList();
  }

  @Override
  public Optional<QnaExecutionIntentStateView> loadLatestActiveByResourceForUpdate(
      QnaExecutionResourceType resourceType, String resourceId) {
    return executionIntentPersistencePort
        .findLatestActiveByResourceForUpdate(toExecutionResourceType(resourceType), resourceId)
        .map(this::toView);
  }

  @Override
  public boolean hasConflictingActiveIntent(
      QnaExecutionResourceType resourceType,
      String resourceId,
      QnaExecutionActionType requestedActionType) {
    return executionIntentPersistencePort.existsActiveByResourceAndActionTypeNotForUpdate(
        toExecutionResourceType(resourceType),
        resourceId,
        momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType.valueOf(
            requestedActionType.name()));
  }

  @Override
  public List<QnaExecutionIntentStateView> loadActiveByResourceForUpdate(
      QnaExecutionResourceType resourceType, String resourceId) {
    return executionIntentPersistencePort
        .findActiveByResourceForUpdate(toExecutionResourceType(resourceType), resourceId)
        .stream()
        .map(this::toView)
        .toList();
  }

  private ExecutionResourceType toExecutionResourceType(QnaExecutionResourceType resourceType) {
    return ExecutionResourceType.valueOf(resourceType.name());
  }

  private QnaExecutionIntentStateView toView(ExecutionIntent intent) {
    return new QnaExecutionIntentStateView(
        intent.getPublicId(),
        QnaExecutionActionType.valueOf(intent.getActionType().name()),
        intent.getStatus());
  }
}
