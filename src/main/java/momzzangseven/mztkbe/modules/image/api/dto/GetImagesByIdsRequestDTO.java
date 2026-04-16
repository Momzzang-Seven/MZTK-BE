package momzzangseven.mztkbe.modules.image.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsCommand;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

public record GetImagesByIdsRequestDTO(
    @NotEmpty(message = "ids must not be empty")
        List<
                @NotNull(message = "ids must not contain null")
                @Positive(message = "ids must be positive") Long>
            ids,
    @NotNull(message = "referenceType must not be null") ImageReferenceType referenceType,
    @NotNull(message = "referenceId must not be null")
        @Positive(message = "referenceId must be positive")
        Long referenceId) {

  public GetImagesByIdsCommand toCommand(Long userId) {
    return new GetImagesByIdsCommand(userId, referenceType, referenceId, ids);
  }
}
