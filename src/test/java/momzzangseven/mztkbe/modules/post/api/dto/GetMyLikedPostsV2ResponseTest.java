package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyLikedPostsV2Response unit test")
class GetMyLikedPostsV2ResponseTest {

  @Test
  @DisplayName("maps application result to response")
  void from() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostListResult post =
        new PostListResult(
            1L,
            PostType.FREE,
            null,
            "content",
            3L,
            2L,
            true,
            10L,
            "writer",
            null,
            0L,
            false,
            List.of("routine"),
            List.of(),
            now,
            now);
    GetMyLikedPostsCursorResult result =
        new GetMyLikedPostsCursorResult(List.of(post), true, "next");

    GetMyLikedPostsV2Response response = GetMyLikedPostsV2Response.from(result);

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("next");
    assertThat(response.posts()).hasSize(1);
    assertThat(response.posts().getFirst().postId()).isEqualTo(1L);
    assertThat(response.posts().getFirst().isLiked()).isTrue();
  }
}
