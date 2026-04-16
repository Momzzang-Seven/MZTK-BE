package momzzangseven.mztkbe.modules.image.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusCommand;

public record GetImagesStatusRequestDTO(
    @NotEmpty(message = "ids must not be empty")
        List<
                @NotNull(message = "ids must not contain null")
                @Positive(message = "ids must be positive") Long>
            ids) {

  public GetImagesStatusCommand toCommand(Long userId) {
    return new GetImagesStatusCommand(userId, ids);
  }
}
