package momzzangseven.mztkbe.modules.answer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerMutationResponse unit test")
class AnswerMutationResponseTest {

  @Test
  @DisplayName("from(AnswerMutationResult) maps answer mutation web3 envelope")
  void from_mapsFields() {
    AnswerMutationResponse response =
        AnswerMutationResponse.from(
            new AnswerMutationResult(
                10L,
                20L,
                new AnswerExecutionWriteView(
                    new AnswerExecutionWriteView.Resource("ANSWER", "20", "PENDING_EXECUTION"),
                    "QNA_ANSWER_UPDATE",
                    new AnswerExecutionWriteView.ExecutionIntent(
                        "intent-20", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
                    new AnswerExecutionWriteView.Execution("EIP7702", 2),
                    null,
                    false)));

    assertThat(response.postId()).isEqualTo(10L);
    assertThat(response.answerId()).isEqualTo(20L);
    assertThat(response.web3()).isNotNull();
    assertThat(response.web3().actionType()).isEqualTo("QNA_ANSWER_UPDATE");
  }
}
