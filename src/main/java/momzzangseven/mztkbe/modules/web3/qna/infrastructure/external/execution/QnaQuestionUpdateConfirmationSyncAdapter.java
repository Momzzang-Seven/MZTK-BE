package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateConfirmationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnAnyExecutionEnabled
public class QnaQuestionUpdateConfirmationSyncAdapter
    implements QnaQuestionUpdateConfirmationSyncPort {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final QnaQuestionUpdateStatePersistencePort statePersistencePort;
  private final ExecutionActionHandlerPort actionHandler;

  public QnaQuestionUpdateConfirmationSyncAdapter(
      ExecutionIntentPersistencePort executionIntentPersistencePort,
      QnaQuestionUpdateStatePersistencePort statePersistencePort,
      @Qualifier("qnaEscrowExecutionActionHandlerAdapter")
          ExecutionActionHandlerPort actionHandler) {
    this.executionIntentPersistencePort = executionIntentPersistencePort;
    this.statePersistencePort = statePersistencePort;
    this.actionHandler = actionHandler;
  }

  @Override
  @Transactional
  public boolean syncConfirmedQuestionUpdate(String executionIntentPublicId) {
    return executionIntentPersistencePort
        .findByPublicId(executionIntentPublicId)
        .filter(this::isConfirmedQuestionUpdate)
        .map(
            intent -> {
              actionHandler.afterExecutionConfirmed(intent, actionHandler.buildActionPlan(intent));
              return isNoLongerReconciliationCandidate(executionIntentPublicId);
            })
        .orElse(false);
  }

  private boolean isConfirmedQuestionUpdate(ExecutionIntent intent) {
    return intent.getStatus() == ExecutionIntentStatus.CONFIRMED
        && intent.getActionType() == ExecutionActionType.QNA_QUESTION_UPDATE;
  }

  private boolean isNoLongerReconciliationCandidate(String executionIntentPublicId) {
    return statePersistencePort
        .findByExecutionIntentPublicIdForUpdate(executionIntentPublicId)
        .map(state -> state.getStatus() != QnaQuestionUpdateStateStatus.INTENT_BOUND)
        .orElse(false);
  }
}
