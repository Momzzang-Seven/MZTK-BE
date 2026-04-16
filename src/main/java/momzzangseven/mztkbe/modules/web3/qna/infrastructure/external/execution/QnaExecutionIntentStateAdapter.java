package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Bridges shared execution intent persistence into qna-owned conflict/recovery state checks. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
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
        .findLatestActiveByResourceForUpdate(toExecutionResourceType(resourceType), resourceId)
        .map(this::toView);
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
