package momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostEntity unit test")
class PostEntityTest {

  @Test
  @DisplayName("builder defaults solved flag when null")
  void builderDefaultsNullableFields() {
    PostEntity entity =
        PostEntity.builder()
            .id(1L)
            .userId(2L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .isSolved(null)
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
            .isSolved(false)
            .tags(List.of("java"))
            .build();

    PostEntity entity = PostEntity.fromDomain(post);

    assertThat(entity.getId()).isEqualTo(10L);
    assertThat(entity.getUserId()).isEqualTo(5L);
    assertThat(entity.getType()).isEqualTo(PostType.QUESTION);
    assertThat(entity.getReward()).isEqualTo(30L);
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
            .isSolved(true)
            .build();

    Post post = entity.toDomain(List.of("spring"));

    assertThat(post.getId()).isEqualTo(9L);
    assertThat(post.getUserId()).isEqualTo(1L);
    assertThat(post.getType()).isEqualTo(PostType.FREE);
    assertThat(post.getTitle()).isEqualTo("title");
    assertThat(post.getContent()).isEqualTo("content");
    assertThat(post.getReward()).isZero();
    assertThat(post.getIsSolved()).isTrue();
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
            .isSolved(false)
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
            .isSolved(false)
            .build();

    Post post = entity.toDomain();

    assertThat(post.getTags()).isEmpty();
  }
}
