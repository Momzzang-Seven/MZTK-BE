package momzzangseven.mztkbe.modules.image.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsCommand;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetImagesByIdsRequestDTO unit test")
class GetImagesByIdsRequestDTOTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("toCommand() maps query params")
  void toCommand_mapsQueryParams() {
    GetImagesByIdsRequestDTO request =
        new GetImagesByIdsRequestDTO(List.of(1L, 2L), ImageReferenceType.COMMUNITY_FREE, 100L);

    GetImagesByIdsCommand command = request.toCommand(7L);

    assertThat(command.userId()).isEqualTo(7L);
    assertThat(command.referenceType()).isEqualTo(ImageReferenceType.COMMUNITY_FREE);
    assertThat(command.referenceId()).isEqualTo(100L);
    assertThat(command.ids()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("validation rejects null referenceType")
  void validation_rejectsNullReferenceType() {
    GetImagesByIdsRequestDTO request = new GetImagesByIdsRequestDTO(List.of(1L), null, 100L);

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("referenceType must not be null");
  }

  @Test
  @DisplayName("validation rejects non-positive referenceId")
  void validation_rejectsNonPositiveReferenceId() {
    GetImagesByIdsRequestDTO request =
        new GetImagesByIdsRequestDTO(List.of(1L), ImageReferenceType.COMMUNITY_FREE, 0L);

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("referenceId must be positive");
  }

  @Test
  @DisplayName("validation rejects non-positive ids")
  void validation_rejectsNonPositiveIds() {
    GetImagesByIdsRequestDTO request =
        new GetImagesByIdsRequestDTO(List.of(-1L), ImageReferenceType.COMMUNITY_FREE, 1L);

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getMessage())
        .contains("ids must be positive");
  }
}
