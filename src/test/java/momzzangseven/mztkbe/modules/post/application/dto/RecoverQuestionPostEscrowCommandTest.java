package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecoverQuestionPostEscrowCommand unit test")
class RecoverQuestionPostEscrowCommandTest {

  @Test
  @DisplayName("validate rejects invalid requesterId")
  void validate_rejectsInvalidRequesterId() {
    RecoverQuestionPostEscrowCommand command = new RecoverQuestionPostEscrowCommand(0L, 10L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("requesterId must be positive");
  }

  @Test
  @DisplayName("validate rejects invalid postId")
  void validate_rejectsInvalidPostId() {
    RecoverQuestionPostEscrowCommand command = new RecoverQuestionPostEscrowCommand(1L, 0L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("postId must be positive");
  }

  @Test
  @DisplayName("validate accepts valid input")
  void validate_acceptsValidInput() {
    RecoverQuestionPostEscrowCommand command = new RecoverQuestionPostEscrowCommand(1L, 10L);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
