package momzzangseven.mztkbe.modules.answer.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaExecutionResumeBatchViewQuery;
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
@DisplayName("AnswerExecutionResumeAdapter unit test")
class AnswerExecutionResumeAdapterTest {

  @Mock private GetQnaExecutionResumeViewUseCase getQnaExecutionResumeViewUseCase;

  @InjectMocks private AnswerExecutionResumeAdapter adapter;

  @Test
  @DisplayName("loadLatest maps qna resume view into answer-owned read view")
  void loadLatest_mapsResult() {
    given(
            getQnaExecutionResumeViewUseCase.execute(
                new GetQnaExecutionResumeViewQuery(QnaExecutionResourceType.ANSWER, 20L)))
        .willReturn(Optional.of(qnaResume("20")));

    var result = adapter.loadLatest(20L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().resource().type()).isEqualTo("ANSWER");
    assertThat(result.orElseThrow().actionType()).isEqualTo("QNA_ANSWER_UPDATE");
    assertThat(result.orElseThrow().transaction()).isNull();
  }

  @Test
  @DisplayName("loadLatestByAnswerIds maps qna resume views in batch")
  void loadLatestByAnswerIds_mapsResults() {
    given(
            getQnaExecutionResumeViewUseCase.executeBatch(
                new GetQnaExecutionResumeBatchViewQuery(
                    QnaExecutionResourceType.ANSWER, List.of(20L, 21L))))
        .willReturn(Map.of(20L, qnaResume("20"), 21L, qnaResume("21")));

    var result = adapter.loadLatestByAnswerIds(List.of(20L, 21L, 20L));

    assertThat(result).hasSize(2);
    assertThat(result.get(20L).resource().id()).isEqualTo("20");
    assertThat(result.get(21L).actionType()).isEqualTo("QNA_ANSWER_UPDATE");
  }

  private QnaExecutionResumeViewResult qnaResume(String id) {
    return new QnaExecutionResumeViewResult(
        new QnaExecutionResumeViewResult.Resource(
            QnaExecutionResourceType.ANSWER, id, QnaExecutionResourceStatus.PENDING_EXECUTION),
        "QNA_ANSWER_UPDATE",
        new QnaExecutionResumeViewResult.ExecutionIntent(
            "intent-" + id, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
        new QnaExecutionResumeViewResult.Execution("EIP7702", 2),
        null);
  }
}
