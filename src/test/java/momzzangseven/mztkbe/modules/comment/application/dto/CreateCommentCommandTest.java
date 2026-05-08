package momzzangseven.mztkbe.modules.comment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateCommentCommand unit test")
class CreateCommentCommandTest {

  @Test
  @DisplayName("constructor accepts content longer than 1000 chars")
  void constructor_acceptsLongContent() {
    String content = "a".repeat(5000);

    CreateCommentCommand command = new CreateCommentCommand(1L, 2L, null, content);

    assertThat(command.postId()).isEqualTo(1L);
    assertThat(command.userId()).isEqualTo(2L);
    assertThat(command.parentId()).isNull();
    assertThat(command.content()).hasSize(5000);
  }

  @Test
  @DisplayName("constructor defaults null targetType to POST target command")
  void constructor_defaultsNullTargetTypeToPost() {
    CreateCommentCommand command =
        new CreateCommentCommand(null, 1L, null, 2L, null, "post comment");

    assertThat(command.targetType()).isEqualTo(CommentTargetType.POST);
    assertThat(command.postId()).isEqualTo(1L);
    assertThat(command.answerId()).isNull();
    assertThat(command.userId()).isEqualTo(2L);
    assertThat(command.content()).isEqualTo("post comment");
  }

  @Test
  @DisplayName("forPost() creates POST target command with postId and no answerId")
  void forPost_createsPostTargetCommand() {
    CreateCommentCommand command = CreateCommentCommand.forPost(1L, 2L, 3L, "post comment");

    assertThat(command.targetType()).isEqualTo(CommentTargetType.POST);
    assertThat(command.postId()).isEqualTo(1L);
    assertThat(command.answerId()).isNull();
    assertThat(command.userId()).isEqualTo(2L);
    assertThat(command.parentId()).isEqualTo(3L);
    assertThat(command.content()).isEqualTo("post comment");
  }

  @Test
  @DisplayName("forAnswer() creates ANSWER target command with answerId and no postId")
  void forAnswer_createsAnswerTargetCommand() {
    CreateCommentCommand command = CreateCommentCommand.forAnswer(10L, 20L, 30L, "answer comment");

    assertThat(command.targetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(command.postId()).isNull();
    assertThat(command.answerId()).isEqualTo(10L);
    assertThat(command.userId()).isEqualTo(20L);
    assertThat(command.parentId()).isEqualTo(30L);
    assertThat(command.content()).isEqualTo("answer comment");
  }

  @Test
  @DisplayName("constructor rejects null or blank required fields")
  void constructor_rejectsMissingRequiredFields() {
    assertThatThrownBy(() -> new CreateCommentCommand(null, 2L, null, "content"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> new CreateCommentCommand(1L, null, null, "content"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> new CreateCommentCommand(1L, 2L, null, null))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> new CreateCommentCommand(1L, 2L, null, " "))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
  }

  @Test
  @DisplayName("target factories reject missing target ids")
  void targetFactories_rejectMissingTargetIds() {
    assertThatThrownBy(() -> CreateCommentCommand.forPost(null, 2L, null, "content"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> CreateCommentCommand.forAnswer(null, 2L, null, "content"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
  }

  @Test
  @DisplayName("POST target command rejects answerId")
  void constructor_rejectsPostTargetWithAnswerId() {
    assertThatThrownBy(
            () ->
                new CreateCommentCommand(CommentTargetType.POST, 1L, 10L, 2L, null, "post comment"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
  }

  @Test
  @DisplayName("ANSWER target command rejects postId")
  void constructor_rejectsAnswerTargetWithPostId() {
    assertThatThrownBy(
            () ->
                new CreateCommentCommand(
                    CommentTargetType.ANSWER, 1L, 10L, 2L, null, "answer comment"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
  }

  @Test
  @DisplayName("null targetType defaults to POST and rejects answerId")
  void constructor_rejectsDefaultPostTargetWithAnswerId() {
    assertThatThrownBy(() -> new CreateCommentCommand(null, 1L, 10L, 2L, null, "post comment"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
  }
}
