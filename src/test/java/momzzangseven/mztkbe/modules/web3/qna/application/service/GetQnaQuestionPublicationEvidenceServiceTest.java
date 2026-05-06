package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaQuestionPublicationEvidenceQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetQnaQuestionPublicationEvidenceService unit test")
class GetQnaQuestionPublicationEvidenceServiceTest {

  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;

  @InjectMocks private GetQnaQuestionPublicationEvidenceService service;

  @Test
  @DisplayName("evidence includes projection state and latest create intent id")
  void evidenceIncludesProjectionStateAndLatestCreateIntentId() {
    String rootKey =
        QnaEscrowIdempotencyKeyFactory.create(
            QnaExecutionActionType.QNA_QUESTION_CREATE, 7L, 101L, null);
    QnaQuestionProjection deleted =
        QnaQuestionProjection.create(
                101L,
                7L,
                "question-101",
                "0x" + "1".repeat(40),
                BigInteger.TEN,
                "0x" + "a".repeat(64))
            .markDeletedWithAnswers();
    when(qnaProjectionPersistencePort.findQuestionByPostId(101L)).thenReturn(Optional.of(deleted));
    when(loadQnaExecutionIntentStatePort.loadLatestByRootIdempotencyKey(rootKey))
        .thenReturn(
            Optional.of(
                new QnaExecutionIntentStateView(
                    "intent-1",
                    QnaExecutionActionType.QNA_QUESTION_CREATE,
                    ExecutionIntentStatus.CONFIRMED)));

    var result = service.execute(new GetQnaQuestionPublicationEvidenceQuery(101L, 7L));

    assertThat(result.projectionExists()).isTrue();
    assertThat(result.projectionState()).isEqualTo("DELETED_WITH_ANSWERS");
    assertThat(result.latestCreateIntentStatus()).isEqualTo("CONFIRMED");
    assertThat(result.latestCreateExecutionIntentId()).isEqualTo("intent-1");
  }
}
