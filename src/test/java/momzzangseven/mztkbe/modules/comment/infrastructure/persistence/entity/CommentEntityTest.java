package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
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
  @DisplayName("toDomain() maps normal POST row")
  void toDomain_mapsPostTargetRow() {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
    LocalDateTime updatedAt = LocalDateTime.now();
    CommentEntity entity =
        CommentEntity.builder()
            .id(1L)
            .targetType(CommentTargetType.POST)
            .postId(100L)
            .writerId(200L)
            .content("post comment")
            .isDeleted(false)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    Comment domain = entity.toDomain();

    assertThat(domain.getTargetType()).isEqualTo(CommentTargetType.POST);
    assertThat(domain.getPostId()).isEqualTo(100L);
    assertThat(domain.getAnswerId()).isNull();
  }

  @Test
  @DisplayName("toDomain() maps normal ANSWER row with root post id")
  void toDomain_mapsAnswerTargetRow() {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
    LocalDateTime updatedAt = LocalDateTime.now();
    CommentEntity entity =
        CommentEntity.builder()
            .id(1L)
            .targetType(CommentTargetType.ANSWER)
            .postId(100L)
            .answerId(300L)
            .writerId(200L)
            .content("answer comment")
            .isDeleted(false)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    Comment domain = entity.toDomain();

    assertThat(domain.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(domain.getPostId()).isEqualTo(100L);
    assertThat(domain.getAnswerId()).isEqualTo(300L);
  }

  @Test
  @DisplayName("from() keeps root post id for answer comments")
  void from_answerCommentKeepsRootPostId() {
    LocalDateTime now = LocalDateTime.now();
    Comment domain = Comment.createForAnswer(100L, 300L, 200L, null, "answer comment");

    CommentEntity entity = CommentEntity.from(domain, null);
    Comment mapped = entity.toDomain();

    assertThat(entity.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(entity.getPostId()).isEqualTo(100L);
    assertThat(entity.getAnswerId()).isEqualTo(300L);
    assertThat(mapped.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(mapped.getPostId()).isEqualTo(100L);
    assertThat(mapped.getAnswerId()).isEqualTo(300L);
    assertThat(mapped.getCreatedAt()).isAfter(now.minusSeconds(1));
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
