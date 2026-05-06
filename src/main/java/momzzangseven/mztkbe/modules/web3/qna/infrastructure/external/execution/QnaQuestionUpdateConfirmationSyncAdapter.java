package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateConfirmationSyncPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class QnaQuestionUpdateConfirmationSyncAdapter
    implements QnaQuestionUpdateConfirmationSyncPort {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final QnaEscrowExecutionActionHandlerAdapter actionHandlerAdapter;

  @Override
  @Transactional
  public boolean syncConfirmedQuestionUpdate(String executionIntentPublicId) {
    return executionIntentPersistencePort
        .findByPublicId(executionIntentPublicId)
        .filter(this::isConfirmedQuestionUpdate)
        .map(
            intent -> {
              actionHandlerAdapter.afterExecutionConfirmed(
                  intent, actionHandlerAdapter.buildActionPlan(intent));
              return true;
            })
        .orElse(false);
  }

  private boolean isConfirmedQuestionUpdate(ExecutionIntent intent) {
    return intent.getStatus() == ExecutionIntentStatus.CONFIRMED
        && intent.getActionType() == ExecutionActionType.QNA_QUESTION_UPDATE;
  }
}
