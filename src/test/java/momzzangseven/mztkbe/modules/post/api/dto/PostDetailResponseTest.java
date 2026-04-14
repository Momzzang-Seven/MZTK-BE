package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionResumeView;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostDetailResponse unit test")
class PostDetailResponseTest {

  @Test
  @DisplayName("from(PostDetailResult) includes question web3Execution for question posts")
  void from_mapsQuestionInfo() {
    PostDetailResponse response =
        PostDetailResponse.from(
            new PostDetailResult(
                10L,
                PostType.QUESTION,
                "title",
                "content",
                3L,
                true,
                7L,
                "writer",
                null,
                List.of("img"),
                50L,
                true,
                new QuestionExecutionResumeView(
                    new QuestionExecutionResumeView.Resource("QUESTION", "10", "PENDING_EXECUTION"),
                    "QNA_ANSWER_ACCEPT",
                    new QuestionExecutionResumeView.ExecutionIntent(
                        "intent-10", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
                    new QuestionExecutionResumeView.Execution("EIP7702", 2),
                    null),
                List.of("tag"),
                LocalDateTime.of(2026, 4, 14, 9, 0),
                LocalDateTime.of(2026, 4, 14, 10, 0)));

    assertThat(response.question()).isNotNull();
    assertThat(response.question().reward()).isEqualTo(50L);
    assertThat(response.question().isSolved()).isTrue();
    assertThat(response.question().web3Execution()).isNotNull();
    assertThat(response.question().web3Execution().actionType()).isEqualTo("QNA_ANSWER_ACCEPT");
  }
}
