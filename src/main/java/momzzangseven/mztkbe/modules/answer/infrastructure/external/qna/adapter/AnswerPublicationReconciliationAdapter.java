package momzzangseven.mztkbe.modules.answer.infrastructure.external.qna.adapter;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort.DeleteCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort.CreateCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort.UpdateCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAnswerPublicationEvidence;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaAnswerPublicationEvidenceUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RepairQuestionAnswerCountsUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnswerPublicationReconciliationAdapter implements AnswerPublicationReconciliationPort {

  private final AnswerPublicationReconciliationStatePort statePort;
  private final AnswerUpdateImagePort answerUpdateImagePort;
  private final GetQnaAnswerPublicationEvidenceUseCase qnaEvidenceUseCase;
  private final RepairQuestionAnswerCountsUseCase repairQuestionAnswerCountsUseCase;
  private final PlatformTransactionManager transactionManager;

  @Override
  public boolean tryAcquireReconciliationLock() {
    return statePort.tryAcquireReconciliationLock();
  }

  @Override
  public int reconcileConfirmedSubmits(int batchSize) {
    int updated = 0;
    for (CreateCandidate candidate : statePort.findPendingSubmitCandidates(batchSize)) {
      updated +=
          executeCandidate(
              "answer submit confirmation reconciliation",
              candidate.answerId(),
              () -> {
                QnaAnswerPublicationEvidence evidence = evidence(candidate);
                if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_SUBMIT)
                    && evidence.isConfirmed()
                    && evidence.answerProjectionExists()) {
                  return statePort.confirmSubmitIfCurrent(
                      candidate.answerId(), candidate.executionIntentId());
                }
                return 0;
              });
    }
    return updated;
  }

  @Override
  public int reconcileTerminalSubmitFailures(int batchSize) {
    int updated = 0;
    for (CreateCandidate candidate : statePort.findPendingSubmitCandidates(batchSize)) {
      updated +=
          executeCandidate(
              "answer submit terminal failure reconciliation",
              candidate.answerId(),
              () -> {
                QnaAnswerPublicationEvidence evidence = evidence(candidate);
                if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_SUBMIT)
                    && evidence.isTerminalFailure()
                    && !evidence.answerProjectionExists()) {
                  return statePort.failSubmitIfCurrent(
                      candidate.answerId(),
                      candidate.executionIntentId(),
                      evidence.status().name(),
                      failureReason(evidence, "answer submit terminal reconciliation"));
                }
                return 0;
              });
    }
    return updated;
  }

  @Override
  public int reconcileConfirmedUpdates(int batchSize) {
    int updated = 0;
    for (UpdateCandidate candidate : statePort.findIntentBoundUpdateCandidates(batchSize)) {
      try {
        updated +=
            executeInRequiresNew(
                () -> {
                  QnaAnswerPublicationEvidence evidence = evidence(candidate);
                  if (!matches(evidence, QnaExecutionActionType.QNA_ANSWER_UPDATE)
                      || !evidence.isConfirmed()) {
                    return 0;
                  }
                  int contentUpdated =
                      statePort.applyConfirmedUpdateContentIfCurrent(
                          candidate.stateId(),
                          candidate.answerId(),
                          candidate.executionIntentId(),
                          candidate.pendingContent());
                  if (contentUpdated == 0) {
                    return 0;
                  }
                  answerUpdateImagePort.applyPendingImages(
                      candidate.stateId(), candidate.answerUserId(), candidate.answerId());
                  return statePort.markUpdateConfirmedIfCurrent(
                      candidate.stateId(), candidate.executionIntentId());
                });
      } catch (RuntimeException ex) {
        log.warn(
            "answer update image reconciliation failed: stateId={}, answerId={}, intentId={}",
            candidate.stateId(),
            candidate.answerId(),
            candidate.executionIntentId(),
            ex);
        executeCandidate(
            "answer update reconciliation-required mark",
            candidate.answerId(),
            () ->
                statePort.markUpdateReconciliationRequiredIfCurrent(
                    candidate.stateId(),
                    candidate.executionIntentId(),
                    "confirmed answer update image reconciliation failed"));
      }
    }
    return updated;
  }

  @Override
  public int reconcileTerminalUpdateFailures(int batchSize) {
    int updated = 0;
    for (UpdateCandidate candidate : statePort.findIntentBoundUpdateCandidates(batchSize)) {
      updated +=
          executeCandidate(
              "answer update terminal failure reconciliation",
              candidate.answerId(),
              () -> {
                QnaAnswerPublicationEvidence evidence = evidence(candidate);
                if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_UPDATE)
                    && evidence.isTerminalFailure()) {
                  return statePort.failUpdateIfCurrent(
                      candidate.stateId(),
                      candidate.executionIntentId(),
                      evidence.status().name(),
                      failureReason(evidence, "answer update terminal reconciliation"));
                }
                return 0;
              });
    }
    return updated;
  }

  @Override
  public List<DeleteCandidate> findConfirmedDeleteCandidates(int batchSize) {
    return statePort.findPendingDeleteCandidates(batchSize).stream()
        .map(
            candidate -> {
              Optional<DeleteCandidate> confirmed =
                  executeOptionalCandidate(
                      "answer delete confirmation evidence reconciliation",
                      candidate.answerId(),
                      () -> {
                        QnaAnswerPublicationEvidence evidence = evidence(candidate);
                        if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_DELETE)
                            && evidence.isConfirmed()) {
                          return Optional.of(
                              new DeleteCandidate(
                                  candidate.answerId(), candidate.executionIntentId()));
                        }
                        return Optional.empty();
                      });
              return confirmed;
            })
        .flatMap(Optional::stream)
        .toList();
  }

  @Override
  public List<Long> deleteConfirmedDeleteAnswers(List<DeleteCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    return candidates.stream()
        .map(
            candidate ->
                statePort.deleteConfirmedDeleteAnswer(
                    new AnswerPublicationReconciliationStatePort.DeleteCandidate(
                        candidate.answerId(), candidate.executionIntentId())))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Override
  public int reconcileTerminalDeleteRollbacks(int batchSize) {
    int updated = 0;
    for (AnswerPublicationReconciliationStatePort.DeleteCandidate candidate :
        statePort.findPendingDeleteCandidates(batchSize)) {
      updated +=
          executeCandidate(
              "answer delete terminal rollback reconciliation",
              candidate.answerId(),
              () -> {
                QnaAnswerPublicationEvidence evidence = evidence(candidate);
                if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_DELETE)
                    && evidence.isTerminalFailure()) {
                  return statePort.rollbackDeleteIfCurrent(
                      candidate.answerId(),
                      candidate.executionIntentId(),
                      evidence.status().name(),
                      failureReason(evidence, "answer delete terminal reconciliation"));
                }
                return 0;
              });
    }
    return updated;
  }

  @Override
  public int repairQuestionAnswerCounts() {
    return repairQuestionAnswerCountsUseCase.repairQuestionAnswerCounts();
  }

  private QnaAnswerPublicationEvidence evidence(CreateCandidate candidate) {
    return qnaEvidenceUseCase.getAnswerPublicationEvidence(
        candidate.answerId(), candidate.executionIntentId());
  }

  private QnaAnswerPublicationEvidence evidence(UpdateCandidate candidate) {
    return qnaEvidenceUseCase.getAnswerPublicationEvidence(
        candidate.answerId(), candidate.executionIntentId());
  }

  private QnaAnswerPublicationEvidence evidence(
      AnswerPublicationReconciliationStatePort.DeleteCandidate candidate) {
    return qnaEvidenceUseCase.getAnswerPublicationEvidence(
        candidate.answerId(), candidate.executionIntentId());
  }

  private boolean matches(
      QnaAnswerPublicationEvidence evidence, QnaExecutionActionType actionType) {
    return evidence.actionType() == actionType;
  }

  private String failureReason(QnaAnswerPublicationEvidence evidence, String fallback) {
    return evidence.failureReason() == null || evidence.failureReason().isBlank()
        ? fallback
        : evidence.failureReason();
  }

  private int executeCandidate(String operation, Long answerId, Supplier<Integer> action) {
    try {
      return executeInRequiresNew(action);
    } catch (RuntimeException ex) {
      log.warn("{} failed: answerId={}", operation, answerId, ex);
      return 0;
    }
  }

  private Optional<DeleteCandidate> executeOptionalCandidate(
      String operation, Long answerId, Supplier<Optional<DeleteCandidate>> action) {
    try {
      return executeInRequiresNew(action);
    } catch (RuntimeException ex) {
      log.warn("{} failed: answerId={}", operation, answerId, ex);
      return Optional.empty();
    }
  }

  private <T> T executeInRequiresNew(Supplier<T> action) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template.execute(status -> action.get());
  }
}
