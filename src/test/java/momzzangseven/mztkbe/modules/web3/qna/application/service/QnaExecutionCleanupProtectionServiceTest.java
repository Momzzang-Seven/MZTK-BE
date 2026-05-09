package momzzangseven.mztkbe.modules.web3.qna.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.CheckQnaAnswerCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.CheckQnaQuestionCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionCleanupIntentPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionCleanupIntentPort.QnaExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort.QnaAnswerExecutionIntentRef;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionCleanupProtectionQueryPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaExecutionCleanupProtectionService")
class QnaExecutionCleanupProtectionServiceTest {

  @Mock private LoadQnaExecutionCleanupIntentPort loadIntentPort;
  @Mock private CheckQnaQuestionCleanupProtectionPort questionProtectionPort;
  @Mock private CheckQnaAnswerCleanupProtectionPort answerProtectionPort;
  @Mock private QnaExecutionCleanupProtectionQueryPort qnaQueryPort;

  @InjectMocks private QnaExecutionCleanupProtectionService service;

  @Test
  @DisplayName("filters out intents that still have local recovery evidence")
  void filterDeletableFinalizedIntentIds_filtersProtectedIntents() {
    List<Long> candidateIds = List.of(1L, 2L, 3L, 4L, 5L);
    when(loadIntentPort.loadByIds(candidateIds))
        .thenReturn(
            List.of(
                intent(
                    1L,
                    "question-failed",
                    QnaExecutionResourceType.QUESTION,
                    "101",
                    QnaExecutionActionType.QNA_QUESTION_CREATE),
                intent(
                    2L,
                    "answer-create",
                    QnaExecutionResourceType.ANSWER,
                    "201",
                    QnaExecutionActionType.QNA_ANSWER_SUBMIT),
                intent(
                    3L,
                    "answer-ref",
                    QnaExecutionResourceType.ANSWER,
                    "202",
                    QnaExecutionActionType.QNA_ANSWER_SUBMIT),
                intent(
                    4L,
                    "answer-update",
                    QnaExecutionResourceType.ANSWER,
                    "203",
                    QnaExecutionActionType.QNA_ANSWER_UPDATE),
                intent(
                    5L,
                    "free",
                    QnaExecutionResourceType.ANSWER,
                    "204",
                    QnaExecutionActionType.QNA_ANSWER_SUBMIT)));
    when(questionProtectionPort.hasFailedQuestionCreate(101L, 7L)).thenReturn(true);
    when(answerProtectionPort.hasCurrentCreateIntent(anyString())).thenReturn(false);
    doReturn(true).when(answerProtectionPort).hasCurrentCreateIntent("answer-create");
    when(answerProtectionPort.hasCurrentDeleteIntent(anyString())).thenReturn(false);
    when(qnaQueryPort.hasProtectedAnswerUpdateState(anyString())).thenReturn(false);
    doReturn(true).when(qnaQueryPort).hasProtectedAnswerUpdateState("answer-update");
    when(qnaQueryPort.findAnswerExecutionIntentRef(anyString())).thenReturn(Optional.empty());
    doReturn(
            Optional.of(
                new QnaAnswerExecutionIntentRef(
                    "answer-ref", 101L, 202L, QnaExecutionActionType.QNA_ANSWER_SUBMIT, "FAILED")))
        .when(qnaQueryPort)
        .findAnswerExecutionIntentRef("answer-ref");
    when(answerProtectionPort.hasFailedAnswer(202L)).thenReturn(true);

    List<Long> result = service.filterDeletableFinalizedIntentIds(candidateIds);

    assertThat(result).containsExactly(5L);
  }

  private QnaExecutionCleanupIntent intent(
      Long id,
      String executionIntentId,
      QnaExecutionResourceType resourceType,
      String resourceId,
      QnaExecutionActionType actionType) {
    return new QnaExecutionCleanupIntent(
        id, executionIntentId, resourceType, resourceId, actionType, 7L);
  }
}
