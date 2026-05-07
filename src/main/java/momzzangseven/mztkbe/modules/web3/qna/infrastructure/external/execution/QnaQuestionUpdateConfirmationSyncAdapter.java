package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateConfirmationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnAnyExecutionEnabled
public class QnaQuestionUpdateConfirmationSyncAdapter
    implements QnaQuestionUpdateConfirmationSyncPort {

  private final ReplayConfirmedExecutionIntentUseCase replayConfirmedExecutionIntentUseCase;
  private final QnaQuestionUpdateStatePersistencePort statePersistencePort;

  public QnaQuestionUpdateConfirmationSyncAdapter(
      ReplayConfirmedExecutionIntentUseCase replayConfirmedExecutionIntentUseCase,
      QnaQuestionUpdateStatePersistencePort statePersistencePort) {
    this.replayConfirmedExecutionIntentUseCase = replayConfirmedExecutionIntentUseCase;
    this.statePersistencePort = statePersistencePort;
  }

  @Override
  @Transactional
  public boolean syncConfirmedQuestionUpdate(String executionIntentPublicId) {
    boolean replayed =
        replayConfirmedExecutionIntentUseCase.execute(
            new ReplayConfirmedExecutionIntentCommand(
                executionIntentPublicId, QnaExecutionActionType.QNA_QUESTION_UPDATE.name()));
    return replayed && isNoLongerReconciliationCandidate(executionIntentPublicId);
  }

  private boolean isNoLongerReconciliationCandidate(String executionIntentPublicId) {
    return statePersistencePort
        .findByExecutionIntentPublicIdForUpdate(executionIntentPublicId)
        .map(state -> state.getStatus() != QnaQuestionUpdateStateStatus.INTENT_BOUND)
        .orElse(false);
  }
}
