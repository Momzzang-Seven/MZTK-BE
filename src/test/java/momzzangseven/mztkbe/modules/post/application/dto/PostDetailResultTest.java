package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostDetailResult unit test")
class PostDetailResultTest {

  @Test
  @DisplayName("fromDomain maps all fields and derives solved from open status")
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
    List<PostImageResult.PostImageSlot> images =
        List.of(
            new PostImageResult.PostImageSlot(1L, "https://cdn.example.com/img1.webp"),
            new PostImageResult.PostImageSlot(2L, "https://cdn.example.com/img2.webp"));

    PostDetailResult result =
        PostDetailResult.fromDomain(post, 3L, 4L, true, nickname, profileImageUrl, images, null);

    assertThat(result.postId()).isEqualTo(100L);
    assertThat(result.userId()).isEqualTo(7L);
    assertThat(result.type()).isEqualTo(PostType.QUESTION);
    assertThat(result.title()).isEqualTo("title");
    assertThat(result.content()).isEqualTo("content");
    assertThat(result.images())
        .containsExactly(
            new PostImageResult.PostImageSlot(1L, "https://cdn.example.com/img1.webp"),
            new PostImageResult.PostImageSlot(2L, "https://cdn.example.com/img2.webp"));
    assertThat(result.likeCount()).isEqualTo(3L);
    assertThat(result.commentCount()).isEqualTo(4L);
    assertThat(result.liked()).isTrue();
    assertThat(result.reward()).isEqualTo(50L);
    assertThat(result.isSolved()).isFalse();
    assertThat(result.tags()).containsExactly("java");
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.updatedAt()).isEqualTo(updatedAt);
    assertThat(result.nickname()).isEqualTo(nickname);
    assertThat(result.profileImageUrl()).isEqualTo(profileImageUrl);
  }

  @Test
  @DisplayName("fromDomain derives solved true from resolved status")
  void fromDomainDerivesSolvedTrueFromResolvedStatus() {
    Post post =
        Post.builder()
            .id(1L)
            .userId(2L)
            .type(PostType.QUESTION)
            .title("t")
            .content("c")
            .reward(10L)
            .acceptedAnswerId(5L)
            .status(PostStatus.RESOLVED)
            .build();

    String nickname = "test nick name";
    String profileImageUrl = "test/image/url";

    PostDetailResult result =
        PostDetailResult.fromDomain(post, 0L, 0L, false, nickname, profileImageUrl, null, null);

    assertThat(result.isSolved()).isTrue();
    assertThat(result.images()).isEmpty();
  }

  @Test
  @DisplayName("fromDomain derives solved true from pending accept status")
  void fromDomainDerivesSolvedTrueFromPendingAcceptStatus() {
    Post post =
        Post.builder()
            .id(2L)
            .userId(3L)
            .type(PostType.QUESTION)
            .title("pending")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(6L)
            .status(PostStatus.PENDING_ACCEPT)
            .build();

    PostDetailResult result =
        PostDetailResult.fromDomain(post, 0L, 0L, false, "writer", "profile", null, null);

    assertThat(result.isSolved()).isTrue();
  }

  @Test
  @DisplayName("fromDomain derives solved true from pending admin refund status")
  void fromDomainDerivesSolvedTrueFromPendingAdminRefundStatus() {
    Post post =
        Post.builder()
            .id(3L)
            .userId(4L)
            .type(PostType.QUESTION)
            .title("refund pending")
            .content("content")
            .reward(10L)
            .status(PostStatus.PENDING_ADMIN_REFUND)
            .build();

    PostDetailResult result =
        PostDetailResult.fromDomain(post, 0L, 0L, false, "writer", "profile", null, null);

    assertThat(result.isSolved()).isTrue();
  }
}
