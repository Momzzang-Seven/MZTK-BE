package momzzangseven.mztkbe.modules.answer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAnswerRequest unit test")
class CreateAnswerRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("toCommand() maps imageIds")
  void toCommand_mapsImageIds() {
    CreateAnswerRequest request = new CreateAnswerRequest("answer content", List.of(1L, 2L));

    CreateAnswerCommand command = request.toCommand(10L, 20L);

    assertThat(command.postId()).isEqualTo(10L);
    assertThat(command.userId()).isEqualTo(20L);
    assertThat(command.content()).isEqualTo("answer content");
    assertThat(command.imageIds()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("toCommand() preserves null imageIds")
  void toCommand_preservesNullImageIds() {
    CreateAnswerRequest request = new CreateAnswerRequest("answer content", null);

    CreateAnswerCommand command = request.toCommand(10L, 20L);

    assertThat(command.imageIds()).isNull();
  }

  @Test
  @DisplayName("validation rejects null imageId elements")
  void validation_rejectsNullImageIdElements() {
    CreateAnswerRequest request =
        new CreateAnswerRequest("answer content", Arrays.asList(1L, null));

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("Image ID must not be null.");
  }

  @Test
  @DisplayName("validation rejects non-positive imageIds")
  void validation_rejectsNonPositiveImageIds() {
    CreateAnswerRequest request = new CreateAnswerRequest("answer content", List.of(0L));

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("Image ID must be positive.");
  }
}
