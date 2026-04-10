package momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostEntity unit test")
class PostEntityTest {

  @Test
  @DisplayName("builder derives solved flag from status")
  void builderDerivesSolvedFlagFromStatus() {
    PostEntity entity =
        PostEntity.builder()
            .id(1L)
            .userId(2L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .isSolved(true)
            .build();

    assertThat(entity.getIsSolved()).isFalse();
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
    assertThat(entity.getIsSolved()).isFalse();
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
    assertThat(post.getIsSolved()).isFalse();
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
  @DisplayName("resolved question without accepted answer is normalized to open on rehydration")
  void resolvedQuestionEntityIsNormalizedToOpenWhenAcceptedAnswerMissing() {
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

    Post post = entity.toDomain();

    assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(post.getAcceptedAnswerId()).isNull();
    assertThat(post.getIsSolved()).isFalse();
  }

  @Test
  @DisplayName("question open with accepted answer is normalized to resolved on rehydration")
  void questionOpenWithAcceptedAnswerNormalizesToResolved() {
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

    Post post = entity.toDomain();

    assertThat(post.getStatus()).isEqualTo(PostStatus.RESOLVED);
    assertThat(post.getAcceptedAnswerId()).isEqualTo(99L);
    assertThat(post.getIsSolved()).isTrue();
  }

  @Test
  @DisplayName("free post accepted answer is cleared on rehydration")
  void freePostAcceptedAnswerIsClearedOnRehydration() {
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

    Post post = entity.toDomain();

    assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
    assertThat(post.getAcceptedAnswerId()).isNull();
    assertThat(post.getIsSolved()).isFalse();
  }
}
