package momzzangseven.mztkbe.modules.post.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostUnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Post unit test")
class PostTest {

  @Test
  @DisplayName("free post creation forces reward to zero and initializes defaults")
  void createFreePostInitializesDefaults() {
    Post post = Post.create(1L, PostType.FREE, null, "content", 99L, null, null);

    assertThat(post.getReward()).isZero();
    assertThat(post.getTitle()).isNull();
    assertThat(post.getIsSolved()).isFalse();
    assertThat(post.getImageUrls()).isEmpty();
    assertThat(post.getTags()).isEmpty();
    assertThat(post.getCreatedAt()).isNotNull();
    assertThat(post.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("question post requires positive reward")
  void createQuestionRequiresPositiveReward() {
    assertThatThrownBy(
            () -> Post.create(1L, PostType.QUESTION, "title", "content", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("질문 게시글은 보상(XP)이 필요합니다.");

    assertThatThrownBy(() -> Post.create(1L, PostType.QUESTION, "title", "content", 0L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("질문 게시글은 보상(XP)이 필요합니다.");
  }

  @Test
  @DisplayName("question post keeps provided positive reward")
  void createQuestionWithPositiveReward() {
    Post post =
        Post.create(
            1L,
            PostType.QUESTION,
            "question title",
            "question content",
            25L,
            List.of("img1"),
            List.of("tag1"));

    assertThat(post.getReward()).isEqualTo(25L);
    assertThat(post.getType()).isEqualTo(PostType.QUESTION);
    assertThat(post.getIsSolved()).isFalse();
  }

  @Test
  @DisplayName("create validates mandatory input fields")
  void createValidatesMandatoryFields() {
    assertThatThrownBy(() -> Post.create(null, PostType.FREE, "title", "content", 0L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("작성자 ID는 필수입니다.");

    assertThatThrownBy(() -> Post.create(1L, null, "title", "content", 0L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("게시글 타입은 필수입니다.");

    assertThatThrownBy(() -> Post.create(1L, PostType.FREE, "title", " ", 0L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("내용을 입력해주세요.");

    assertThatThrownBy(() -> Post.create(1L, PostType.QUESTION, " ", "content", 10L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("질문 게시글은 제목이 필요합니다.");
  }

  @Test
  @DisplayName("create rejects null content (blank과 구별)")
  void createRejectsNullContent() {
    assertThatThrownBy(() -> Post.create(1L, PostType.FREE, null, null, 0L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("내용을 입력해주세요.");
  }

  @Test
  @DisplayName("create QUESTION 게시글에서 title=null이면 예외")
  void createQuestionWithNullTitleThrows() {
    assertThatThrownBy(() -> Post.create(1L, PostType.QUESTION, null, "content", 10L, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("질문 게시글은 제목이 필요합니다.");
  }

  @Test
  @DisplayName("ownership validation blocks non-owner")
  void validateOwnership() {
    Post post = basePost();

    assertThatCode(() -> post.validateOwnership(1L)).doesNotThrowAnyException();
    assertThatThrownBy(() -> post.validateOwnership(2L))
        .isInstanceOf(PostUnauthorizedException.class);
  }

  @Test
  @DisplayName("ownership validation rejects null requester")
  void validateOwnershipWithNullRequester() {
    Post post = basePost();

    assertThatThrownBy(() -> post.validateOwnership(null))
        .isInstanceOf(PostUnauthorizedException.class);
  }

  @Test
  @DisplayName("update with no fields returns same instance")
  void updateReturnsSameInstanceWhenNothingProvided() {
    Post post = basePost();

    Post updated = post.update(null, null, null, null);

    assertThat(updated).isSameAs(post);
  }

  @Test
  @DisplayName("update rejects blank title or content")
  void updateRejectsBlankValues() {
    Post post = basePost();

    assertThatThrownBy(() -> post.update(" ", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("수정할 제목은 비워둘 수 없습니다.");

    assertThatThrownBy(() -> post.update(null, " ", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("수정할 내용은 비워둘 수 없습니다.");
  }

  @Test
  @DisplayName("update replaces fields and refreshes updatedAt")
  void updateChangesFields() {
    Post post = basePost();

    Post updated = post.update("new", "new-content", List.of("img2"), List.of("tag2"));

    assertThat(updated).isNotSameAs(post);
    assertThat(updated.getTitle()).isEqualTo("new");
    assertThat(updated.getContent()).isEqualTo("new-content");
    assertThat(updated.getImageUrls()).containsExactly("img2");
    assertThat(updated.getTags()).containsExactly("tag2");
    assertThat(updated.getUpdatedAt()).isAfter(post.getUpdatedAt());
  }

  @Test
  @DisplayName("withTags handles null as empty without mutating original")
  void withTagsHandlesNull() {
    Post post = basePost();

    Post withNullTags = post.withTags(null);

    assertThat(withNullTags.getTags()).isEmpty();
    assertThat(post.getTags()).containsExactly("tag1");
  }

  @Test
  @DisplayName("constructor defensively defaults null imageUrls and tags")
  void constructorDefaultsNullCollections() {
    Post post =
        Post.builder()
            .id(10L)
            .userId(1L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .isSolved(false)
            .imageUrls(null)
            .tags(null)
            .build();

    assertThat(post.getImageUrls()).isEqualTo(new ArrayList<>());
    assertThat(post.getTags()).isEqualTo(new ArrayList<>());
  }

  private Post basePost() {
    return Post.builder()
        .id(1L)
        .userId(1L)
        .type(PostType.FREE)
        .title("title")
        .content("content")
        .imageUrls(List.of("img1"))
        .reward(0L)
        .isSolved(false)
        .tags(List.of("tag1"))
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }
}
