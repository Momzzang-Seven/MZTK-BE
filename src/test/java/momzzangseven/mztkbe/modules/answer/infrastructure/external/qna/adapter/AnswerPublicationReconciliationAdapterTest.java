package momzzangseven.mztkbe.modules.answer.infrastructure.external.qna.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort.DeleteCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort.UpdateCandidate;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAnswerPublicationEvidence;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaAnswerPublicationEvidenceUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RepairQuestionAnswerCountsUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerPublicationReconciliationAdapter")
class AnswerPublicationReconciliationAdapterTest {

  @Mock private AnswerPublicationReconciliationStatePort statePort;
  @Mock private AnswerUpdateImagePort answerUpdateImagePort;
  @Mock private GetQnaAnswerPublicationEvidenceUseCase evidenceUseCase;
  @Mock private RepairQuestionAnswerCountsUseCase repairUseCase;

  private AnswerPublicationReconciliationAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new AnswerPublicationReconciliationAdapter(
            statePort, answerUpdateImagePort, evidenceUseCase, repairUseCase, new NoOpTxManager());
  }

  @Test
  @DisplayName("confirmed delete candidates preserve expected execution intent id")
  void findConfirmedDeleteCandidates_preservesIntentId() {
    when(statePort.findPendingDeleteCandidates(100))
        .thenReturn(
            List.of(
                new AnswerPublicationReconciliationStatePort.DeleteCandidate(
                    10L, "intent-delete")));
    when(evidenceUseCase.getAnswerPublicationEvidence(10L, "intent-delete"))
        .thenReturn(
            new QnaAnswerPublicationEvidence(
                10L,
                "intent-delete",
                QnaExecutionActionType.QNA_ANSWER_DELETE,
                QnaExecutionIntentStatus.CONFIRMED,
                null,
                false));

    List<DeleteCandidate> result = adapter.findConfirmedDeleteCandidates(100);

    assertThat(result).containsExactly(new DeleteCandidate(10L, "intent-delete"));
  }

  @Test
  @DisplayName("deleteConfirmedDeleteAnswers deletes only rows matching candidate intent")
  void deleteConfirmedDeleteAnswers_delegatesGuardedDelete() {
    DeleteCandidate candidate = new DeleteCandidate(10L, "intent-delete");
    when(statePort.deleteConfirmedDeleteAnswer(
            new AnswerPublicationReconciliationStatePort.DeleteCandidate(10L, "intent-delete")))
        .thenReturn(10L);

    assertThat(adapter.deleteConfirmedDeleteAnswers(List.of(candidate))).containsExactly(10L);
  }

  @Test
  @DisplayName(
      "confirmed update image failure rolls back content tx and marks reconciliation required")
  void reconcileConfirmedUpdates_imageFailureMarksReconciliationRequired() {
    UpdateCandidate candidate = new UpdateCandidate(1L, 10L, 20L, "intent-update", "next");
    when(statePort.findIntentBoundUpdateCandidates(100)).thenReturn(List.of(candidate));
    when(evidenceUseCase.getAnswerPublicationEvidence(10L, "intent-update"))
        .thenReturn(
            new QnaAnswerPublicationEvidence(
                10L,
                "intent-update",
                QnaExecutionActionType.QNA_ANSWER_UPDATE,
                QnaExecutionIntentStatus.CONFIRMED,
                null,
                true));
    when(statePort.applyConfirmedUpdateContentIfCurrent(1L, 10L, "intent-update", "next"))
        .thenReturn(1);
    doThrow(new RuntimeException("image failed"))
        .when(answerUpdateImagePort)
        .applyPendingImages(1L, 20L, 10L);

    int updated = adapter.reconcileConfirmedUpdates(100);

    assertThat(updated).isZero();
    verify(statePort, never()).markUpdateConfirmedIfCurrent(1L, "intent-update");
    verify(statePort)
        .markUpdateReconciliationRequiredIfCurrent(
            1L, "intent-update", "confirmed answer update image reconciliation failed");
  }

  @Test
  @DisplayName("confirmed update evidence lookup failure stays retryable")
  void reconcileConfirmedUpdates_evidenceFailureDoesNotMarkReconciliationRequired() {
    UpdateCandidate candidate = new UpdateCandidate(1L, 10L, 20L, "intent-update", "next");
    when(statePort.findIntentBoundUpdateCandidates(100)).thenReturn(List.of(candidate));
    when(evidenceUseCase.getAnswerPublicationEvidence(10L, "intent-update"))
        .thenThrow(new RuntimeException("evidence unavailable"));

    int updated = adapter.reconcileConfirmedUpdates(100);

    assertThat(updated).isZero();
    verify(statePort, never())
        .applyConfirmedUpdateContentIfCurrent(1L, 10L, "intent-update", "next");
    verify(answerUpdateImagePort, never()).applyPendingImages(1L, 20L, 10L);
    verify(statePort, never())
        .markUpdateReconciliationRequiredIfCurrent(
            1L, "intent-update", "confirmed answer update image reconciliation failed");
  }

  private static class NoOpTxManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {}

    @Override
    public void rollback(TransactionStatus status) {}
  }
}
