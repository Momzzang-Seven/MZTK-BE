package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DeleteAnswerCommand unit test")
class DeleteAnswerCommandTest {

  @Nested
  @DisplayName("validate")
  class Validate {

    @Test
    @DisplayName("rejects null postId")
    void validate_rejectsNullPostId() {
      assertThatThrownBy(() -> new DeleteAnswerCommand(null, 20L, 30L))
          .isInstanceOf(AnswerInvalidInputException.class)
          .hasMessageContaining("postId is required");
    }

    @Test
    @DisplayName("rejects null answerId")
    void validate_rejectsNullAnswerId() {
      assertThatThrownBy(() -> new DeleteAnswerCommand(10L, null, 30L))
          .isInstanceOf(AnswerInvalidInputException.class)
          .hasMessageContaining("answerId is required");
    }

    @Test
    @DisplayName("rejects null userId")
    void validate_rejectsNullUserId() {
      assertThatThrownBy(() -> new DeleteAnswerCommand(10L, 20L, null))
          .isInstanceOf(AnswerInvalidInputException.class)
          .hasMessageContaining("userId is required");
    }

    @Test
    @DisplayName("accepts valid input")
    void validate_acceptsValidInput() {
      assertThatCode(() -> new DeleteAnswerCommand(10L, 20L, 30L)).doesNotThrowAnyException();
    }
  }
}
