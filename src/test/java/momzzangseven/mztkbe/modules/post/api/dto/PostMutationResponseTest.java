package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostMutationResponse unit test")
class PostMutationResponseTest {

  @Test
  @DisplayName("from(PostMutationResult) maps nullable web3 envelope")
  void from_mapsFields() {
    PostMutationResponse response =
        PostMutationResponse.from(
            new PostMutationResult(
                10L,
                new QuestionExecutionWriteView(
                    new QuestionExecutionWriteView.Resource("QUESTION", "10", "PENDING_EXECUTION"),
                    "QNA_QUESTION_UPDATE",
                    new QuestionExecutionWriteView.ExecutionIntent(
                        "intent-10", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
                    new QuestionExecutionWriteView.Execution("EIP7702", 2),
                    null,
                    false)));

    assertThat(response.postId()).isEqualTo(10L);
    assertThat(response.web3()).isNotNull();
    assertThat(response.web3().actionType()).isEqualTo("QNA_QUESTION_UPDATE");
    assertThat(response.questionUpdate()).isNull();
  }

  @Test
  @DisplayName("from(PostMutationResult) maps retryable question update preparation failure")
  void from_mapsQuestionUpdatePreparationFailure() {
    PostMutationResponse response =
        PostMutationResponse.from(PostMutationResult.questionUpdatePreparationFailed(10L));

    assertThat(response.postId()).isEqualTo(10L);
    assertThat(response.web3()).isNull();
    assertThat(response.questionUpdate()).isNotNull();
    assertThat(response.questionUpdate().status()).isEqualTo("PREPARATION_FAILED");
    assertThat(response.questionUpdate().retryable()).isTrue();
    assertThat(response.questionUpdate().errorCode()).isNull();
  }
}
