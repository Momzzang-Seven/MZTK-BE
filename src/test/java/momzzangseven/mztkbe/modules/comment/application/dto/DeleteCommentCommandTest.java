package momzzangseven.mztkbe.modules.comment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeleteCommentCommand unit test")
class DeleteCommentCommandTest {

  @Test
  @DisplayName("constructor stores ids")
  void constructor_storesIds() {
    DeleteCommentCommand command = new DeleteCommentCommand(10L, 20L);

    assertThat(command.commentId()).isEqualTo(10L);
    assertThat(command.userId()).isEqualTo(20L);
  }

  @Test
  @DisplayName("constructor rejects null ids")
  void constructor_rejectsNullIds() {
    assertThatThrownBy(() -> new DeleteCommentCommand(null, 20L))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());

    assertThatThrownBy(() -> new DeleteCommentCommand(10L, null))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.MISSING_REQUIRED_FIELD.getMessage());
  }
}
