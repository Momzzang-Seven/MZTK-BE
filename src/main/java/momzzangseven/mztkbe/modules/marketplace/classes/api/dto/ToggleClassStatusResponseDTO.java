package momzzangseven.mztkbe.modules.marketplace.classes.api.dto;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ToggleClassStatusResult;

/** HTTP response DTO for class status toggle. */
public record ToggleClassStatusResponseDTO(Long classId, boolean active) {

  public static ToggleClassStatusResponseDTO from(ToggleClassStatusResult result) {
    return new ToggleClassStatusResponseDTO(result.classId(), result.active());
  }
}
