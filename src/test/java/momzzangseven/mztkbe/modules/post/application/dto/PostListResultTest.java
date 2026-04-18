package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PostListResult unit test")
class PostListResultTest {

  @Test
  @DisplayName("[M-1] fromDomain maps list fields and derives solved from open status")
  void fromDomainMapsAndDerivesSolvedFromOpenStatus() {
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
            .status(PostStatus.OPEN)
            .tags(List.of("java"))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    String nickname = "test nick name";
    String profileImageUrl = "test/image/url";

    PostListResult result =
        PostListResult.fromDomain(
            post, 2L, true, nickname, profileImageUrl, List.of("https://cdn/img1.webp"));

    assertThat(result.postId()).isEqualTo(100L);
    assertThat(result.userId()).isEqualTo(7L);
    assertThat(result.type()).isEqualTo(PostType.QUESTION);
    assertThat(result.title()).isEqualTo("title");
    assertThat(result.content()).isEqualTo("content");
    assertThat(result.likeCount()).isEqualTo(2L);
    assertThat(result.liked()).isTrue();
    assertThat(result.reward()).isEqualTo(50L);
    assertThat(result.isSolved()).isFalse();
    assertThat(result.tags()).containsExactly("java");
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.updatedAt()).isEqualTo(updatedAt);
    assertThat(result.nickname()).isEqualTo(nickname);
    assertThat(result.profileImageUrl()).isEqualTo(profileImageUrl);
    assertThat(result.imageUrls()).containsExactly("https://cdn/img1.webp");
  }

  @Test
  @DisplayName("fromDomain derives solved true from resolved status")
  void fromDomainDerivesSolvedTrueFromResolvedStatus() {
    Post post =
        Post.builder()
            .id(101L)
            .userId(8L)
            .type(PostType.QUESTION)
            .title("resolved title")
            .content("resolved content")
            .reward(70L)
            .acceptedAnswerId(11L)
            .status(PostStatus.RESOLVED)
            .build();

    PostListResult result =
        PostListResult.fromDomain(post, 1L, false, "writer", "profile", List.of());

    assertThat(result.isSolved()).isTrue();
  }

  @Test
  @DisplayName("fromDomain derives solved true from pending accept status")
  void fromDomainDerivesSolvedTrueFromPendingAcceptStatus() {
    Post post =
        Post.builder()
            .id(102L)
            .userId(9L)
            .type(PostType.QUESTION)
            .title("pending title")
            .content("pending content")
            .reward(70L)
            .acceptedAnswerId(12L)
            .status(PostStatus.PENDING_ACCEPT)
            .build();

    PostListResult result =
        PostListResult.fromDomain(post, 1L, false, "writer", "profile", List.of());

    assertThat(result.isSolved()).isTrue();
  }

  @Nested
  @DisplayName("imageUrls null/empty handling")
  class ImageUrlsCoercion {

    private Post basePost(Long id) {
      return Post.builder()
          .id(id)
          .userId(1L)
          .type(PostType.FREE)
          .title("t")
          .content("c")
          .reward(0L)
          .status(PostStatus.OPEN)
          .build();
    }

    @Test
    @DisplayName("[M-1] fromDomain populates imageUrls when provided, in order")
    void fromDomain_withImageUrls_populatesInOrder() {
      PostListResult result =
          PostListResult.fromDomain(
              basePost(1L),
              2L,
              true,
              "n",
              "p",
              List.of("https://cdn/a.webp", "https://cdn/b.webp"));

      assertThat(result.imageUrls())
          .containsExactly("https://cdn/a.webp", "https://cdn/b.webp");
    }

    @Test
    @DisplayName("[M-2] fromDomain coerces null imageUrls to empty list")
    void fromDomain_withNullImageUrls_returnsEmptyList() {
      PostListResult result =
          PostListResult.fromDomain(basePost(1L), 0L, false, "n", "p", null);

      assertThat(result.imageUrls()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("[M-3] fromDomain preserves empty imageUrls list")
    void fromDomain_withEmptyImageUrls_preservesEmpty() {
      PostListResult result =
          PostListResult.fromDomain(basePost(1L), 0L, false, "n", "p", List.of());

      assertThat(result.imageUrls()).isNotNull().isEmpty();
    }
  }
}
