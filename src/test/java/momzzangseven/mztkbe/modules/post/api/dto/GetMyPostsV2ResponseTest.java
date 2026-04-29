package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyPostsV2Response unit test")
class GetMyPostsV2ResponseTest {

  @Test
  @DisplayName("maps application result to response")
  void from() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostListResult post =
        new PostListResult(
            1L,
            PostType.QUESTION,
            "question",
            "content",
            3L,
            2L,
            true,
            10L,
            "writer",
            null,
            100L,
            true,
            List.of("squat"),
            List.of(),
            now,
            now);
    GetMyPostsCursorResult result = new GetMyPostsCursorResult(List.of(post), true, "next");

    GetMyPostsV2Response response = GetMyPostsV2Response.from(result);

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("next");
    assertThat(response.posts()).hasSize(1);
    assertThat(response.posts().getFirst().postId()).isEqualTo(1L);
    assertThat(response.posts().getFirst().question().reward()).isEqualTo(100L);
    assertThat(response.posts().getFirst().question().isSolved()).isTrue();
  }
}
