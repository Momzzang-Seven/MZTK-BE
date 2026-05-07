package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.modules.answer.application.port.in.ReconcileAnswerPublicationUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconcileAnswerPublicationService implements ReconcileAnswerPublicationUseCase {

  private final AnswerPublicationReconciliationPort answerPublicationReconciliationPort;
  private final PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;

  @Override
  @Transactional
  public ReconcileAnswerPublicationResult reconcile(int batchSize) {
    if (batchSize <= 0) {
      throw new AnswerInvalidInputException("batchSize must be positive.");
    }
    int confirmedSubmits = answerPublicationReconciliationPort.reconcileConfirmedSubmits(batchSize);
    int terminalSubmitFailures =
        answerPublicationReconciliationPort.reconcileTerminalSubmitFailures(batchSize);
    int confirmedUpdates = answerPublicationReconciliationPort.reconcileConfirmedUpdates(batchSize);
    List<Long> confirmedDeleteAnswerIds =
        answerPublicationReconciliationPort.findConfirmedDeleteAnswerIds(batchSize);
    int confirmedDeletes =
        answerPublicationReconciliationPort.deleteConfirmedDeleteAnswers(confirmedDeleteAnswerIds);
    confirmedDeleteAnswerIds.forEach(
        answerId -> publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(answerId)));
    answerPublicationReconciliationPort.repairQuestionAnswerCounts();
    return new ReconcileAnswerPublicationResult(
        confirmedSubmits, terminalSubmitFailures, confirmedUpdates, confirmedDeletes);
  }
}
