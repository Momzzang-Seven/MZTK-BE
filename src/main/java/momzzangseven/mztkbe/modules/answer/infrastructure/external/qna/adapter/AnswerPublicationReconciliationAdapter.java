package momzzangseven.mztkbe.modules.answer.infrastructure.external.qna.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort.CreateCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort.DeleteCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort.UpdateCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAnswerPublicationEvidence;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaAnswerPublicationEvidenceUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnswerPublicationReconciliationAdapter implements AnswerPublicationReconciliationPort {

  private final AnswerPublicationReconciliationStatePort statePort;
  private final AnswerUpdateImagePort answerUpdateImagePort;
  private final GetQnaAnswerPublicationEvidenceUseCase qnaEvidenceUseCase;

  @Override
  public boolean tryAcquireReconciliationLock() {
    return statePort.tryAcquireReconciliationLock();
  }

  @Override
  public int reconcileConfirmedSubmits(int batchSize) {
    int updated = 0;
    for (CreateCandidate candidate : statePort.findPendingSubmitCandidates(batchSize)) {
      QnaAnswerPublicationEvidence evidence = evidence(candidate);
      if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_SUBMIT)
          && evidence.isConfirmed()
          && evidence.answerProjectionExists()) {
        updated +=
            statePort.confirmSubmitIfCurrent(candidate.answerId(), candidate.executionIntentId());
      }
    }
    return updated;
  }

  @Override
  public int reconcileTerminalSubmitFailures(int batchSize) {
    int updated = 0;
    for (CreateCandidate candidate : statePort.findPendingSubmitCandidates(batchSize)) {
      QnaAnswerPublicationEvidence evidence = evidence(candidate);
      if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_SUBMIT)
          && evidence.isTerminalFailure()
          && !evidence.answerProjectionExists()) {
        updated +=
            statePort.failSubmitIfCurrent(
                candidate.answerId(),
                candidate.executionIntentId(),
                evidence.status().name(),
                failureReason(evidence, "answer submit terminal reconciliation"));
      }
    }
    return updated;
  }

  @Override
  public int reconcileConfirmedUpdates(int batchSize) {
    int updated = 0;
    for (UpdateCandidate candidate : statePort.findIntentBoundUpdateCandidates(batchSize)) {
      QnaAnswerPublicationEvidence evidence = evidence(candidate);
      if (!matches(evidence, QnaExecutionActionType.QNA_ANSWER_UPDATE) || !evidence.isConfirmed()) {
        continue;
      }
      int contentUpdated =
          statePort.applyConfirmedUpdateContentIfCurrent(
              candidate.stateId(),
              candidate.answerId(),
              candidate.executionIntentId(),
              candidate.pendingContent());
      if (contentUpdated == 0) {
        continue;
      }
      try {
        answerUpdateImagePort.applyPendingImages(
            candidate.stateId(), candidate.answerUserId(), candidate.answerId());
        updated +=
            statePort.markUpdateConfirmedIfCurrent(
                candidate.stateId(), candidate.executionIntentId());
      } catch (RuntimeException ex) {
        log.warn(
            "answer update image reconciliation failed: stateId={}, answerId={}, intentId={}",
            candidate.stateId(),
            candidate.answerId(),
            candidate.executionIntentId(),
            ex);
        statePort.markUpdateReconciliationRequiredIfCurrent(
            candidate.stateId(),
            candidate.executionIntentId(),
            "confirmed answer update image reconciliation failed");
      }
    }
    return updated;
  }

  @Override
  public int reconcileTerminalUpdateFailures(int batchSize) {
    int updated = 0;
    for (UpdateCandidate candidate : statePort.findIntentBoundUpdateCandidates(batchSize)) {
      QnaAnswerPublicationEvidence evidence = evidence(candidate);
      if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_UPDATE)
          && evidence.isTerminalFailure()) {
        updated +=
            statePort.failUpdateIfCurrent(
                candidate.stateId(),
                candidate.executionIntentId(),
                evidence.status().name(),
                failureReason(evidence, "answer update terminal reconciliation"));
      }
    }
    return updated;
  }

  @Override
  public List<Long> findConfirmedDeleteAnswerIds(int batchSize) {
    return statePort.findPendingDeleteCandidates(batchSize).stream()
        .filter(
            candidate -> {
              QnaAnswerPublicationEvidence evidence = evidence(candidate);
              return matches(evidence, QnaExecutionActionType.QNA_ANSWER_DELETE)
                  && evidence.isConfirmed();
            })
        .map(DeleteCandidate::answerId)
        .toList();
  }

  @Override
  public List<Long> deleteConfirmedDeleteAnswers(List<Long> answerIds) {
    return statePort.deleteConfirmedDeleteAnswers(answerIds);
  }

  @Override
  public int reconcileTerminalDeleteRollbacks(int batchSize) {
    int updated = 0;
    for (DeleteCandidate candidate : statePort.findPendingDeleteCandidates(batchSize)) {
      QnaAnswerPublicationEvidence evidence = evidence(candidate);
      if (matches(evidence, QnaExecutionActionType.QNA_ANSWER_DELETE)
          && evidence.isTerminalFailure()) {
        updated +=
            statePort.rollbackDeleteIfCurrent(
                candidate.answerId(),
                candidate.executionIntentId(),
                evidence.status().name(),
                failureReason(evidence, "answer delete terminal reconciliation"));
      }
    }
    return updated;
  }

  @Override
  public int repairQuestionAnswerCounts() {
    return qnaEvidenceUseCase.repairQuestionAnswerCounts();
  }

  private QnaAnswerPublicationEvidence evidence(CreateCandidate candidate) {
    return qnaEvidenceUseCase.getAnswerPublicationEvidence(
        candidate.answerId(), candidate.executionIntentId());
  }

  private QnaAnswerPublicationEvidence evidence(UpdateCandidate candidate) {
    return qnaEvidenceUseCase.getAnswerPublicationEvidence(
        candidate.answerId(), candidate.executionIntentId());
  }

  private QnaAnswerPublicationEvidence evidence(DeleteCandidate candidate) {
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
}
