package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateAnswerCommand unit test")
class UpdateAnswerCommandTest {

  @Test
  @DisplayName("constructor preserves ordered imageIds")
  void constructor_preservesOrderedImageIds() {
    UpdateAnswerCommand command =
        new UpdateAnswerCommand(10L, 30L, 20L, "updated", List.of(1L, 2L));

    assertThat(command.postId()).isEqualTo(10L);
    assertThat(command.answerId()).isEqualTo(30L);
    assertThat(command.userId()).isEqualTo(20L);
    assertThat(command.content()).isEqualTo("updated");
    assertThat(command.imageIds()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("constructor preserves null imageIds")
  void constructor_preservesNullImageIds() {
    UpdateAnswerCommand command = new UpdateAnswerCommand(10L, 30L, 20L, "updated", null);

    assertThat(command.imageIds()).isNull();
  }

  @Test
  @DisplayName("validate accepts image-only updates")
  void validate_acceptsImageOnlyUpdates() {
    assertThatCode(() -> new UpdateAnswerCommand(10L, 30L, 20L, null, List.of(1L)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate allows duplicate imageIds on update")
  void validate_allowsDuplicateImageIdsOnUpdate() {
    assertThatCode(() -> new UpdateAnswerCommand(10L, 30L, 20L, null, List.of(1L, 1L)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate rejects fully empty updates")
  void validate_rejectsFullyEmptyUpdates() {
    assertThatThrownBy(() -> new UpdateAnswerCommand(10L, 30L, 20L, null, null))
        .isInstanceOf(AnswerInvalidInputException.class)
        .hasMessageContaining("At least one field must be provided for update");
  }

  @Test
  @DisplayName("validate rejects blank content")
  void validate_rejectsBlankContent() {
    assertThatThrownBy(() -> new UpdateAnswerCommand(10L, 30L, 20L, " ", null))
        .isInstanceOf(AnswerInvalidInputException.class)
        .hasMessageContaining("Updated content must not be blank");
  }
}
