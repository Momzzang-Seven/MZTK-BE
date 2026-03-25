package momzzangseven.mztkbe.modules.answer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateAnswerRequest unit test")
class UpdateAnswerRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("toCommand() maps imageIds")
  void toCommand_mapsImageIds() {
    UpdateAnswerRequest request = new UpdateAnswerRequest("updated", List.of(1L, 2L));

    UpdateAnswerCommand command = request.toCommand(10L, 30L, 20L);

    assertThat(command.postId()).isEqualTo(10L);
    assertThat(command.answerId()).isEqualTo(30L);
    assertThat(command.userId()).isEqualTo(20L);
    assertThat(command.content()).isEqualTo("updated");
    assertThat(command.imageIds()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("toCommand() preserves null imageIds")
  void toCommand_preservesNullImageIds() {
    UpdateAnswerRequest request = new UpdateAnswerRequest("updated", null);

    UpdateAnswerCommand command = request.toCommand(10L, 30L, 20L);

    assertThat(command.imageIds()).isNull();
  }

  @Test
  @DisplayName("validation rejects null imageId elements")
  void validation_rejectsNullImageIdElements() {
    UpdateAnswerRequest request = new UpdateAnswerRequest(null, Arrays.asList(1L, null));

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("Image ID must not be null.");
  }

  @Test
  @DisplayName("validation rejects non-positive imageIds")
  void validation_rejectsNonPositiveImageIds() {
    UpdateAnswerRequest request = new UpdateAnswerRequest(null, List.of(-1L));

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("Image ID must be positive.");
  }
}
