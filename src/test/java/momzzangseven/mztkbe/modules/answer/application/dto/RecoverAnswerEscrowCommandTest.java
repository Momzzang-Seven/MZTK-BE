package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecoverAnswerEscrowCommand unit test")
class RecoverAnswerEscrowCommandTest {

  @Test
  @DisplayName("validate rejects invalid userId")
  void validate_rejectsInvalidUserId() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(0L, 10L, 20L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(AnswerInvalidInputException.class)
        .hasMessageContaining("userId is required");
  }

  @Test
  @DisplayName("validate rejects invalid postId")
  void validate_rejectsInvalidPostId() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(1L, 0L, 20L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(AnswerInvalidInputException.class)
        .hasMessageContaining("postId is required");
  }

  @Test
  @DisplayName("validate rejects invalid answerId")
  void validate_rejectsInvalidAnswerId() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(1L, 10L, 0L);

    assertThatThrownBy(command::validate)
        .isInstanceOf(AnswerInvalidInputException.class)
        .hasMessageContaining("answerId is required");
  }

  @Test
  @DisplayName("validate accepts valid input")
  void validate_acceptsValidInput() {
    RecoverAnswerEscrowCommand command = new RecoverAnswerEscrowCommand(1L, 10L, 20L);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }
}
