package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetQnaAnswerPublicationEvidenceService")
class GetQnaAnswerPublicationEvidenceServiceTest {

  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;

  @InjectMocks private GetQnaAnswerPublicationEvidenceService service;

  @Test
  @DisplayName("evidence combines answer projection existence and execution intent state")
  void getAnswerPublicationEvidence_combinesProjectionAndIntentState() {
    when(qnaProjectionPersistencePort.findAnswerByAnswerId(10L))
        .thenReturn(Optional.of(answerProjection()));
    when(loadQnaExecutionIntentStatePort.loadByExecutionIntentId("intent-1"))
        .thenReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-1",
                    QnaExecutionActionType.QNA_ANSWER_SUBMIT,
                    QnaExecutionIntentStatus.CONFIRMED,
                    null)));

    var result = service.getAnswerPublicationEvidence(10L, "intent-1");

    assertThat(result.answerProjectionExists()).isTrue();
    assertThat(result.actionType()).isEqualTo(QnaExecutionActionType.QNA_ANSWER_SUBMIT);
    assertThat(result.isConfirmed()).isTrue();
  }

  @Test
  @DisplayName("repairQuestionAnswerCounts delegates to projection persistence")
  void repairQuestionAnswerCounts_delegates() {
    when(qnaProjectionPersistencePort.repairQuestionAnswerCounts()).thenReturn(3);

    assertThat(service.repairQuestionAnswerCounts()).isEqualTo(3);
  }

  private QnaAnswerProjection answerProjection() {
    return QnaAnswerProjection.create(
        10L, 1L, "question-1", "answer-10", 20L, "0x" + "a".repeat(64));
  }
}
