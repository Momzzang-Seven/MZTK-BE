package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeViewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionResumeViewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaExecutionResumeViewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionExecutionResumeAdapter unit test")
class QuestionExecutionResumeAdapterTest {

  @Mock private GetQnaExecutionResumeViewUseCase getQnaExecutionResumeViewUseCase;

  @InjectMocks private QuestionExecutionResumeAdapter adapter;

  @Test
  @DisplayName("loadLatest maps qna resume view into post-owned read view")
  void loadLatest_mapsResult() {
    given(
            getQnaExecutionResumeViewUseCase.execute(
                new GetQnaExecutionResumeViewQuery(QnaExecutionResourceType.QUESTION, 10L)))
        .willReturn(Optional.of(qnaResume()));

    var result = adapter.loadLatest(10L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().resource().type()).isEqualTo("QUESTION");
    assertThat(result.orElseThrow().actionType()).isEqualTo("QNA_QUESTION_UPDATE");
    assertThat(result.orElseThrow().transaction().txHash()).isEqualTo("0xabc");
  }

  private QnaExecutionResumeViewResult qnaResume() {
    return new QnaExecutionResumeViewResult(
        new QnaExecutionResumeViewResult.Resource(
            QnaExecutionResourceType.QUESTION, "10", QnaExecutionResourceStatus.PENDING_EXECUTION),
        "QNA_QUESTION_UPDATE",
        new QnaExecutionResumeViewResult.ExecutionIntent(
            "intent-10", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
        new QnaExecutionResumeViewResult.Execution("EIP7702", 2),
        new QnaExecutionResumeViewResult.Transaction(501L, "SUBMITTED", "0xabc"));
  }
}
