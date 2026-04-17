package momzzangseven.mztkbe.modules.image.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetImagesStatusRequestDTO unit test")
class GetImagesStatusRequestDTOTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("toCommand() preserves ids")
  void toCommand_preservesIds() {
    GetImagesStatusRequestDTO request = new GetImagesStatusRequestDTO(List.of(1L, 2L, 2L));

    GetImagesStatusCommand command = request.toCommand(10L);

    assertThat(command.userId()).isEqualTo(10L);
    assertThat(command.ids()).containsExactly(1L, 2L, 2L);
  }

  @Test
  @DisplayName("validation rejects empty ids")
  void validation_rejectsEmptyIds() {
    GetImagesStatusRequestDTO request = new GetImagesStatusRequestDTO(List.of());

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("ids must not be empty");
  }

  @Test
  @DisplayName("validation rejects non-positive ids")
  void validation_rejectsNonPositiveIds() {
    GetImagesStatusRequestDTO request = new GetImagesStatusRequestDTO(List.of(0L));

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("ids must be positive");
  }
}
