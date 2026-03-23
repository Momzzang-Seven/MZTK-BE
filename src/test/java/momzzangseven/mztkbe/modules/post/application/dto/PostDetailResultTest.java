package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostDetailResult unit test")
class PostDetailResultTest {

  @Test
  @DisplayName("fromDomain maps all fields and defaults null solved to false")
  void fromDomainMapsAndDefaultsSolved() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 1, 12, 0);

    Post post =
        Post.builder()
            .id(100L)
            .userId(7L)
            .type(PostType.QUESTION)
            .title("title")
            .content("content")
            .reward(50L)
            .isSolved(null)
            .tags(List.of("java"))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    String nickname = "test nick name";
    String profileImageUrl = "test/image/url";
    List<String> finalObjectKeys = List.of("img1.webp", "img2.webp");

    PostDetailResult result =
        PostDetailResult.fromDomain(post, nickname, profileImageUrl, finalObjectKeys);

    assertThat(result.postId()).isEqualTo(100L);
    assertThat(result.userId()).isEqualTo(7L);
    assertThat(result.type()).isEqualTo(PostType.QUESTION);
    assertThat(result.title()).isEqualTo("title");
    assertThat(result.content()).isEqualTo("content");
    assertThat(result.finalObjectKeys()).containsExactly("img1.webp", "img2.webp");
    assertThat(result.reward()).isEqualTo(50L);
    assertThat(result.isSolved()).isFalse();
    assertThat(result.tags()).containsExactly("java");
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.updatedAt()).isEqualTo(updatedAt);
    assertThat(result.nickname()).isEqualTo(nickname);
    assertThat(result.profileImageUrl()).isEqualTo(profileImageUrl);
  }

  @Test
  @DisplayName("fromDomain keeps solved true")
  void fromDomainKeepsSolvedTrue() {
    Post post =
        Post.builder()
            .id(1L)
            .userId(2L)
            .type(PostType.FREE)
            .title("t")
            .content("c")
            .reward(0L)
            .isSolved(true)
            .build();

    String nickname = "test nick name";
    String profileImageUrl = "test/image/url";

    PostDetailResult result = PostDetailResult.fromDomain(post, nickname, profileImageUrl, null);

    assertThat(result.isSolved()).isTrue();
  }
}
