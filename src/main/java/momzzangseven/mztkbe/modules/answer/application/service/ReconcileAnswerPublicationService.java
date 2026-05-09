package momzzangseven.mztkbe.modules.answer.application.service;

import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.modules.answer.application.port.in.ReconcileAnswerPublicationUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort.DeleteCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconcileAnswerPublicationService implements ReconcileAnswerPublicationUseCase {

  private final AnswerPublicationReconciliationPort answerPublicationReconciliationPort;
  private final PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;
  private final PlatformTransactionManager transactionManager;

  @Override
  @Transactional
  public ReconcileAnswerPublicationResult reconcile(int batchSize) {
    if (batchSize <= 0) {
      throw new AnswerInvalidInputException("batchSize must be positive.");
    }
    if (!answerPublicationReconciliationPort.tryAcquireReconciliationLock()) {
      return new ReconcileAnswerPublicationResult(0, 0, 0, 0, 0, 0);
    }
    int confirmedSubmits =
        executeStage(
            "confirmed answer submit reconciliation",
            () -> answerPublicationReconciliationPort.reconcileConfirmedSubmits(batchSize));
    int terminalSubmitFailures =
        executeStage(
            "terminal answer submit failure reconciliation",
            () -> answerPublicationReconciliationPort.reconcileTerminalSubmitFailures(batchSize));
    int confirmedUpdates =
        executeStage(
            "confirmed answer update reconciliation",
            () -> answerPublicationReconciliationPort.reconcileConfirmedUpdates(batchSize));
    int terminalUpdateFailures =
        executeStage(
            "terminal answer update failure reconciliation",
            () -> answerPublicationReconciliationPort.reconcileTerminalUpdateFailures(batchSize));
    List<Long> deletedAnswerIds =
        executeDeleteStage(
            () -> {
              List<DeleteCandidate> confirmedDeleteCandidates =
                  answerPublicationReconciliationPort.findConfirmedDeleteCandidates(batchSize);
              List<Long> deletedIds =
                  answerPublicationReconciliationPort.deleteConfirmedDeleteAnswers(
                      confirmedDeleteCandidates);
              deletedIds.forEach(
                  answerId ->
                      publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(answerId)));
              return deletedIds;
            });
    int terminalDeleteRollbacks =
        executeStage(
            "terminal answer delete rollback reconciliation",
            () -> answerPublicationReconciliationPort.reconcileTerminalDeleteRollbacks(batchSize));
    executeStage(
        "question answer count repair",
        answerPublicationReconciliationPort::repairQuestionAnswerCounts);
    return new ReconcileAnswerPublicationResult(
        confirmedSubmits,
        terminalSubmitFailures,
        confirmedUpdates,
        terminalUpdateFailures,
        deletedAnswerIds.size(),
        terminalDeleteRollbacks);
  }

  private int executeStage(String operation, Supplier<Integer> action) {
    try {
      return action.get();
    } catch (RuntimeException ex) {
      log.warn("{} failed during answer publication reconciliation", operation, ex);
      return 0;
    }
  }

  private List<Long> executeDeleteStage(Supplier<List<Long>> action) {
    try {
      return executeInRequiresNew(action);
    } catch (RuntimeException ex) {
      log.warn("confirmed answer delete reconciliation failed", ex);
      return List.of();
    }
  }

  private <T> T executeInRequiresNew(Supplier<T> action) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template.execute(status -> action.get());
  }
}
