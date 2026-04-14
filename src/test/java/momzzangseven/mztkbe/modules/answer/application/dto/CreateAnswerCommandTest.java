package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAnswerCommand unit test")
class CreateAnswerCommandTest {

  @Test
  @DisplayName("constructor preserves ordered imageIds")
  void constructor_preservesOrderedImageIds() {
    CreateAnswerCommand command =
        new CreateAnswerCommand(10L, 20L, "answer content", List.of(1L, 2L));

    assertThat(command.postId()).isEqualTo(10L);
    assertThat(command.userId()).isEqualTo(20L);
    assertThat(command.content()).isEqualTo("answer content");
    assertThat(command.imageIds()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("constructor preserves null imageIds")
  void constructor_preservesNullImageIds() {
    CreateAnswerCommand command = new CreateAnswerCommand(10L, 20L, "answer content", null);

    assertThat(command.imageIds()).isNull();
  }

  @Test
  @DisplayName("validate rejects duplicate imageIds")
  void validate_rejectsDuplicateImageIds() {
    assertThatThrownBy(() -> new CreateAnswerCommand(10L, 20L, "answer content", List.of(1L, 1L)))
        .isInstanceOf(AnswerInvalidInputException.class)
        .hasMessageContaining("Duplicate image IDs are not allowed");
  }

  @Test
  @DisplayName("validate rejects blank content")
  void validate_rejectsBlankContent() {
    assertThatThrownBy(() -> new CreateAnswerCommand(10L, 20L, " ", List.of(1L)))
        .isInstanceOf(AnswerInvalidInputException.class)
        .hasMessageContaining("Answer content must not be blank");
  }

  @Test
  @DisplayName("validate accepts empty imageIds")
  void validate_acceptsEmptyImageIds() {
    assertThatCode(() -> new CreateAnswerCommand(10L, 20L, "answer content", List.of()))
        .doesNotThrowAnyException();
  }
}
