package momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("PostEntity unit test")
class PostEntityTest {

  @Test
  @DisplayName("builder stores explicit status and accepted answer state")
  void builderStoresExplicitStatus() {
    PostEntity entity =
        PostEntity.builder()
            .id(1L)
            .userId(2L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(99L)
            .status(PostStatus.RESOLVED)
            .build();

    assertThat(entity.getStatus()).isEqualTo(PostStatus.RESOLVED);
    assertThat(entity.getAcceptedAnswerId()).isEqualTo(99L);
    assertThat(legacySolvedShadow(entity)).isTrue();
  }

  @Test
  @DisplayName("builder syncs legacy solved shadow for pending accept status")
  void builderSyncsLegacySolvedShadowForPendingAccept() {
    PostEntity entity =
        PostEntity.builder()
            .id(2L)
            .userId(2L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(99L)
            .status(PostStatus.PENDING_ACCEPT)
            .build();

    assertThat(legacySolvedShadow(entity)).isTrue();
  }

  @Test
  @DisplayName("builder keeps legacy solved shadow false for open status")
  void builderKeepsLegacySolvedShadowFalseForOpen() {
    PostEntity entity =
        PostEntity.builder()
            .id(3L)
            .userId(2L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.OPEN)
            .build();

    assertThat(legacySolvedShadow(entity)).isFalse();
  }

  @Test
  @DisplayName("fromDomain copies values")
  void fromDomainCopiesValues() {
    Post post =
        Post.builder()
            .id(10L)
            .userId(5L)
            .type(PostType.QUESTION)
            .title("question")
            .content("body")
            .reward(30L)
            .status(PostStatus.OPEN)
            .tags(List.of("java"))
            .build();

    PostEntity entity = PostEntity.fromDomain(post);

    assertThat(entity.getId()).isEqualTo(10L);
    assertThat(entity.getUserId()).isEqualTo(5L);
    assertThat(entity.getType()).isEqualTo(PostType.QUESTION);
    assertThat(entity.getReward()).isEqualTo(30L);
    assertThat(entity.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(entity.getAcceptedAnswerId()).isNull();
  }

  @Test
  @DisplayName("toDomain maps entity fields and provided tags")
  void toDomainMapsWithTags() {
    PostEntity entity =
        PostEntity.builder()
            .id(9L)
            .userId(1L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    Post post = entity.toDomain(List.of("spring"));

    assertThat(post.getId()).isEqualTo(9L);
    assertThat(post.getUserId()).isEqualTo(1L);
    assertThat(post.getType()).isEqualTo(PostType.FREE);
    assertThat(post.getTitle()).isEqualTo("title");
    assertThat(post.getContent()).isEqualTo("content");
    assertThat(post.getReward()).isZero();
    assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(post.isResolved()).isFalse();
    assertThat(post.getTags()).containsExactly("spring");
    assertThat(post.getCreatedAt()).isNull();
    assertThat(post.getUpdatedAt()).isNull();
  }

  @Test
  @DisplayName("toDomain without tags returns empty tag list")
  void toDomainWithoutTagsUsesEmptyList() {
    PostEntity entity =
        PostEntity.builder()
            .id(20L)
            .userId(3L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    Post post = entity.toDomain();

    assertThat(post.getTags()).isEmpty();
  }

  @Test
  @DisplayName("toDomain maps entity fields with empty tags when none provided")
  void toDomainMapsWithEmptyTags() {
    PostEntity entity =
        PostEntity.builder()
            .id(30L)
            .userId(2L)
            .type(PostType.FREE)
            .title("title")
            .content("body")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    Post post = entity.toDomain();

    assertThat(post.getTags()).isEmpty();
  }

  @Test
  @DisplayName("resolved question without accepted answer is rejected on rehydration")
  void resolvedQuestionEntityWithoutAcceptedAnswerFailsRehydration() {
    PostEntity entity =
        PostEntity.builder()
            .id(40L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .status(PostStatus.RESOLVED)
            .build();

    assertThatThrownBy(entity::toDomain)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("acceptedAnswerId");
  }

  @Test
  @DisplayName("open question with accepted answer is rejected on rehydration")
  void openQuestionWithAcceptedAnswerFailsRehydration() {
    PostEntity entity =
        PostEntity.builder()
            .id(41L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("question")
            .content("content")
            .reward(10L)
            .acceptedAnswerId(99L)
            .status(PostStatus.OPEN)
            .build();

    assertThatThrownBy(entity::toDomain)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("acceptedAnswerId");
  }

  @Test
  @DisplayName("free post with accepted answer is rejected on rehydration")
  void freePostAcceptedAnswerFailsRehydration() {
    PostEntity entity =
        PostEntity.builder()
            .id(42L)
            .userId(1L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .acceptedAnswerId(88L)
            .status(PostStatus.RESOLVED)
            .build();

    assertThatThrownBy(entity::toDomain).isInstanceOf(IllegalArgumentException.class);
  }

  private boolean legacySolvedShadow(PostEntity entity) {
    return (boolean) ReflectionTestUtils.getField(entity, "legacySolvedShadow");
  }
}
