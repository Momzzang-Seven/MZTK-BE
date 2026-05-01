package momzzangseven.mztkbe.modules.marketplace.classes.api.dto;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassResult;

/** HTTP response DTO for class update. */
public record UpdateClassResponseDTO(Long classId) {

  public static UpdateClassResponseDTO from(UpdateClassResult result) {
    return new UpdateClassResponseDTO(result.classId());
  }
}
