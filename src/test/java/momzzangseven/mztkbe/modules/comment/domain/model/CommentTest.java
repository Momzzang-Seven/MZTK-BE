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

    assertThat(comment.getTargetType()).isEqualTo(CommentTargetType.POST);
    assertThat(comment.getPostId()).isEqualTo(1L);
    assertThat(comment.getAnswerId()).isNull();
    assertThat(comment.getWriterId()).isEqualTo(2L);
    assertThat(comment.getParentId()).isNull();
    assertThat(comment.getContent()).isEqualTo("hello");
    assertThat(comment.isDeleted()).isFalse();
    assertThat(comment.getCreatedAt()).isNotNull();
    assertThat(comment.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("createForAnswer() stores root post id and answer id")
  void createForAnswer_storesRootPostAndAnswerId() {
    Comment comment = Comment.createForAnswer(100L, 300L, 2L, null, "answer comment");

    assertThat(comment.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(comment.getPostId()).isEqualTo(100L);
    assertThat(comment.getAnswerId()).isEqualTo(300L);
    assertThat(comment.getWriterId()).isEqualTo(2L);
    assertThat(comment.isDeleted()).isFalse();
  }

  @Test
  @DisplayName("builder defaults null targetType to POST target comment")
  void builder_defaultsNullTargetTypeToPost() {
    Comment comment = Comment.builder().postId(1L).writerId(2L).content("post comment").build();

    assertThat(comment.getTargetType()).isEqualTo(CommentTargetType.POST);
    assertThat(comment.getPostId()).isEqualTo(1L);
    assertThat(comment.getAnswerId()).isNull();
  }

  @Test
  @DisplayName("builder creates ANSWER target comment with root post id and answer id")
  void builder_createsAnswerTargetWithRootPostAndAnswerId() {
    Comment comment =
        Comment.builder()
            .targetType(CommentTargetType.ANSWER)
            .postId(100L)
            .answerId(300L)
            .writerId(2L)
            .content("answer comment")
            .build();

    assertThat(comment.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(comment.getPostId()).isEqualTo(100L);
    assertThat(comment.getAnswerId()).isEqualTo(300L);
  }

  @Test
  @DisplayName("builder rejects POST target with answerId")
  void builder_rejectsPostTargetWithAnswerId() {
    assertThatThrownBy(
            () ->
                Comment.builder()
                    .targetType(CommentTargetType.POST)
                    .postId(1L)
                    .answerId(300L)
                    .writerId(2L)
                    .content("post comment")
                    .build())
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("builder rejects POST target without postId")
  void builder_rejectsPostTargetWithoutPostId() {
    assertThatThrownBy(
            () ->
                Comment.builder()
                    .targetType(CommentTargetType.POST)
                    .writerId(2L)
                    .content("post comment")
                    .build())
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("builder rejects ANSWER target without answerId")
  void builder_rejectsAnswerTargetWithoutAnswerId() {
    assertThatThrownBy(
            () ->
                Comment.builder()
                    .targetType(CommentTargetType.ANSWER)
                    .postId(100L)
                    .writerId(2L)
                    .content("answer comment")
                    .build())
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("builder rejects ANSWER target without root postId")
  void builder_rejectsAnswerTargetWithoutPostId() {
    assertThatThrownBy(
            () ->
                Comment.builder()
                    .targetType(CommentTargetType.ANSWER)
                    .answerId(300L)
                    .writerId(2L)
                    .content("answer comment")
                    .build())
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
  }

  @Test
  @DisplayName("null targetType defaults to POST and rejects answerId")
  void builder_rejectsDefaultPostTargetWithAnswerId() {
    assertThatThrownBy(
            () ->
                Comment.builder()
                    .postId(1L)
                    .answerId(300L)
                    .writerId(2L)
                    .content("post comment")
                    .build())
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.INVALID_INPUT.getMessage());
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
