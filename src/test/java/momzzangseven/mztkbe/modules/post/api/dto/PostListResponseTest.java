package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.response.ImageItemResponse;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult.PostImageSlot;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PostListResponse unit test")
class PostListResponseTest {

  private PostListResult resultWithImages(List<PostImageSlot> images) {
    return new PostListResult(
        10L,
        PostType.FREE,
        "title",
        "content",
        1L,
        4L,
        false,
        7L,
        "writer",
        "profile",
        0L,
        false,
        List.of("tag"),
        images,
        LocalDateTime.of(2026, 4, 18, 9, 0),
        LocalDateTime.of(2026, 4, 18, 10, 0));
  }

  private PostListResult questionResult(long commentCount, long answerCount) {
    Post post =
        Post.builder()
            .id(20L)
            .userId(8L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(100L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.of(2026, 4, 18, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 4, 18, 10, 0))
            .build();
    return PostListResult.fromDomain(
        post, 2L, commentCount, answerCount, false, "writer", null, List.of());
  }

  @Nested
  @DisplayName("from(PostListResult)")
  class From {

    @Test
    @DisplayName("[M-25] converts each slot to ImageItemResponse preserving order")
    void from_convertsSlotsInOrder() {
      PostListResult result =
          resultWithImages(
              List.of(
                  new PostImageSlot(1L, "u1"),
                  new PostImageSlot(2L, "u2"),
                  new PostImageSlot(3L, "u3")));

      PostListResponse response = PostListResponse.from(result);

      assertThat(response.images())
          .containsExactly(
              new ImageItemResponse(1L, "u1"),
              new ImageItemResponse(2L, "u2"),
              new ImageItemResponse(3L, "u3"));
      assertThat(response.tags()).containsExactly("tag");
      assertThat(response.writer().userId()).isEqualTo(7L);
      assertThat(response.writer().nickname()).isEqualTo("writer");
      assertThat(response.commentCount()).isEqualTo(4L);
      assertThat(response.answerCount()).isZero();
      assertThat(response.question()).isNull();
    }

    @Test
    @DisplayName("QUESTION response exposes answerCount separately from commentCount")
    void from_questionMapsAnswerCountSeparately() {
      PostListResponse response = PostListResponse.from(questionResult(3L, 7L));

      assertThat(response.commentCount()).isEqualTo(3L);
      assertThat(response.answerCount()).isEqualTo(7L);
      assertThat(response.question()).isNotNull();
    }

    @Test
    @DisplayName("[M-26] returns empty list when result.images() is null")
    void from_nullImages_returnsEmptyList() {
      PostListResponse response = PostListResponse.from(resultWithImages(null));

      assertThat(response.images()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("[M-27] returns empty list when result.images() is empty")
    void from_emptyImages_returnsEmptyList() {
      PostListResponse response = PostListResponse.from(resultWithImages(List.of()));

      assertThat(response.images()).isNotNull().isEmpty();
    }
  }
}
