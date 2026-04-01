package momzzangseven.mztkbe.modules.comment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateCommentCommand unit test")
class UpdateCommentCommandTest {

  @Test
  @DisplayName("constructor accepts content longer than 1000 chars")
  void constructor_acceptsLongContent() {
    String content = "a".repeat(5000);

    UpdateCommentCommand command = new UpdateCommentCommand(1L, 2L, content);

    assertThat(command.commentId()).isEqualTo(1L);
    assertThat(command.userId()).isEqualTo(2L);
    assertThat(command.content()).hasSize(5000);
  }

  @Test
  @DisplayName("constructor rejects null or blank required fields")
  void constructor_rejectsMissingRequiredFields() {
    assertThatThrownBy(() -> new UpdateCommentCommand(null, 2L, "content"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> new UpdateCommentCommand(1L, null, "content"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> new UpdateCommentCommand(1L, 2L, null))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> new UpdateCommentCommand(1L, 2L, " "))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
  }
}
