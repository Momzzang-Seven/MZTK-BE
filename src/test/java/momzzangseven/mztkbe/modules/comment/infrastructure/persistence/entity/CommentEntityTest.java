package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CommentEntity unit test")
class CommentEntityTest {

  @Test
  @DisplayName("from() maps domain comment and parent entity")
  void from_mapsDomainAndParent() {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
    LocalDateTime updatedAt = LocalDateTime.now();
    Comment domain =
        Comment.builder()
            .id(1L)
            .postId(100L)
            .writerId(200L)
            .parentId(10L)
            .content("reply")
            .isDeleted(false)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    CommentEntity parent =
        CommentEntity.builder()
            .id(10L)
            .postId(100L)
            .writerId(999L)
            .content("parent")
            .isDeleted(false)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    CommentEntity entity = CommentEntity.from(domain, parent);

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getPostId()).isEqualTo(100L);
    assertThat(entity.getWriterId()).isEqualTo(200L);
    assertThat(entity.getParent()).isSameAs(parent);
    assertThat(entity.getContent()).isEqualTo("reply");
    assertThat(entity.isDeleted()).isFalse();
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("toDomain() maps parent id when parent exists")
  void toDomain_mapsParentIdWhenParentExists() {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
    LocalDateTime updatedAt = LocalDateTime.now();
    CommentEntity parent =
        CommentEntity.builder()
            .id(10L)
            .postId(100L)
            .writerId(999L)
            .content("parent")
            .isDeleted(false)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    CommentEntity entity =
        CommentEntity.builder()
            .id(1L)
            .postId(100L)
            .writerId(200L)
            .content("reply")
            .isDeleted(true)
            .parent(parent)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    Comment domain = entity.toDomain();

    assertThat(domain.getId()).isEqualTo(1L);
    assertThat(domain.getPostId()).isEqualTo(100L);
    assertThat(domain.getWriterId()).isEqualTo(200L);
    assertThat(domain.getParentId()).isEqualTo(10L);
    assertThat(domain.getContent()).isEqualTo("reply");
    assertThat(domain.isDeleted()).isTrue();
    assertThat(domain.getCreatedAt()).isEqualTo(createdAt);
    assertThat(domain.getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("builder initializes children list by default")
  void builder_initializesChildrenByDefault() {
    CommentEntity entity =
        CommentEntity.builder()
            .id(1L)
            .postId(100L)
            .writerId(200L)
            .content("content")
            .isDeleted(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    assertThat(entity.getChildren()).isNotNull();
    assertThat(entity.getChildren()).isEmpty();
  }
}
