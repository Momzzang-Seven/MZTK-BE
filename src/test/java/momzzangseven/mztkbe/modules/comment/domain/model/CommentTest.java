package momzzangseven.mztkbe.modules.comment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Comment unit test")
class CommentTest {

  @Test
  @DisplayName("create() makes non-deleted comment with timestamps")
  void create_createsCommentWithDefaults() {
    Comment comment = Comment.create(1L, 2L, null, "hello");

    assertThat(comment.getPostId()).isEqualTo(1L);
    assertThat(comment.getWriterId()).isEqualTo(2L);
    assertThat(comment.getParentId()).isNull();
    assertThat(comment.getContent()).isEqualTo("hello");
    assertThat(comment.isDeleted()).isFalse();
    assertThat(comment.getCreatedAt()).isNotNull();
    assertThat(comment.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("create() rejects blank content")
  void create_blankContent_throwsBusinessException() {
    assertThatThrownBy(() -> Comment.create(1L, 2L, null, " "))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("create() rejects null content")
  void create_nullContent_throwsBusinessException() {
    assertThatThrownBy(() -> Comment.create(1L, 2L, null, null))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("updateContent() rejects blank content")
  void updateContent_blankContent_throwsBusinessException() {
    Comment comment = Comment.create(1L, 2L, null, "before");

    assertThatThrownBy(() -> comment.updateContent(" "))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("updateContent() rejects null content")
  void updateContent_nullContent_throwsBusinessException() {
    Comment comment = Comment.create(1L, 2L, null, "before");

    assertThatThrownBy(() -> comment.updateContent(null))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("updateContent() rejects deleted comment")
  void updateContent_deletedComment_throwsBusinessException() {
    Comment comment = Comment.create(1L, 2L, null, "before");
    comment.delete();

    assertThatThrownBy(() -> comment.updateContent("after"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT.getMessage());
  }

  @Test
  @DisplayName("delete() marks comment deleted and replaces content")
  void delete_marksDeletedAndReplacesContent() {
    Comment comment = Comment.create(1L, 2L, null, "before");

    comment.delete();

    assertThat(comment.isDeleted()).isTrue();
    assertThat(comment.getContent()).isEqualTo("삭제된 댓글입니다.");
    assertThat(comment.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("validateWriter() throws for non-writer")
  void validateWriter_nonWriter_throwsCommentUnauthorizedException() {
    Comment comment = Comment.create(1L, 2L, null, "hello");

    assertThatThrownBy(() -> comment.validateWriter(99L))
        .isInstanceOf(CommentUnauthorizedException.class)
        .hasMessage(ErrorCode.COMMENT_UNAUTHORIZED.getMessage());
  }

  @Test
  @DisplayName("validateWriter() passes for writer")
  void validateWriter_writer_passes() {
    Comment comment = Comment.create(1L, 2L, null, "hello");

    comment.validateWriter(2L);

    assertThat(comment.getWriterId()).isEqualTo(2L);
  }
}
