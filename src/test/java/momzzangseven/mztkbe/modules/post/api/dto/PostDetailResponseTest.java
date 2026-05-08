package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.response.ImageItemResponse;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult.PostImageSlot;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionResumeView;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PostDetailResponse unit test")
class PostDetailResponseTest {

  private PostDetailResult questionResult(List<PostImageSlot> images) {
    return new PostDetailResult(
        10L,
        PostType.QUESTION,
        "title",
        "content",
        3L,
        6L,
        8L,
        true,
        7L,
        "writer",
        null,
        images,
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
        LocalDateTime.of(2026, 4, 14, 10, 0));
  }

  @Test
  @DisplayName("from(PostDetailResult) includes question web3Execution for question posts")
  void from_mapsQuestionInfo() {
    PostDetailResponse response =
        PostDetailResponse.from(
            questionResult(List.of(new PostImageResult.PostImageSlot(1L, "img"))));

    assertThat(response.question()).isNotNull();
    assertThat(response.question().reward()).isEqualTo(50L);
    assertThat(response.question().isSolved()).isTrue();
    assertThat(response.question().web3Execution()).isNotNull();
    assertThat(response.question().web3Execution().actionType()).isEqualTo("QNA_ANSWER_ACCEPT");
    assertThat(response.commentCount()).isEqualTo(6L);
    assertThat(response.answerCount()).isEqualTo(8L);
  }

  @Nested
  @DisplayName("from(PostDetailResult)")
  class From {

    @Test
    @DisplayName("[M-29] maps all slots preserving order and populates question branch")
    void from_mapsAllSlotsAndQuestionBranch() {
      PostDetailResponse response =
          PostDetailResponse.from(
              questionResult(List.of(new PostImageSlot(1L, "u1"), new PostImageSlot(2L, "u2"))));

      assertThat(response.images())
          .containsExactly(new ImageItemResponse(1L, "u1"), new ImageItemResponse(2L, "u2"));
      assertThat(response.question()).isNotNull();
      assertThat(response.question().reward()).isEqualTo(50L);
      assertThat(response.question().isSolved()).isTrue();
    }

    @Test
    @DisplayName("[M-30] returns empty images list when result.images() is null")
    void from_nullImages_returnsEmptyList() {
      PostDetailResponse response = PostDetailResponse.from(questionResult(null));

      assertThat(response.images()).isNotNull().isEmpty();
    }
  }
}
