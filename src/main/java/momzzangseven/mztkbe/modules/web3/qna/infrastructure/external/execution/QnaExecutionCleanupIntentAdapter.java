package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentCleanupViewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionCleanupIntentPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionCleanupIntentPort.QnaExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class QnaExecutionCleanupIntentAdapter implements LoadQnaExecutionCleanupIntentPort {

  private final GetExecutionIntentCleanupViewUseCase getExecutionIntentCleanupViewUseCase;

  @Override
  public List<QnaExecutionCleanupIntent> loadByIds(List<Long> intentIds) {
    return getExecutionIntentCleanupViewUseCase.getCleanupViewsByIds(intentIds).stream()
        .map(
            view ->
                new QnaExecutionCleanupIntent(
                    view.id(),
                    view.publicId(),
                    QnaExecutionResourceType.valueOf(view.resourceType().name()),
                    view.resourceId(),
                    QnaExecutionActionType.valueOf(view.actionType().name()),
                    view.requesterUserId()))
        .toList();
  }
}
