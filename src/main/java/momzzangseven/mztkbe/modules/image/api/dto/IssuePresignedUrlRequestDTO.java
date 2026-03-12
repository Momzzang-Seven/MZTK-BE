package momzzangseven.mztkbe.modules.image.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

public record IssuePresignedUrlRequestDTO(
    @NotNull(message = "referenceType must not be null") ImageReferenceType referenceType,
    @NotEmpty(message = "images must not be empty")
        List<@NotBlank(message = "image filename must not be blank") String> images) {
  public IssuePresignedUrlCommand toCommand(Long userId) {
    return new IssuePresignedUrlCommand(userId, referenceType, images);
  }
}
