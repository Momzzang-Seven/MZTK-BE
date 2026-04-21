package momzzangseven.mztkbe.modules.marketplace.api.dto;

import momzzangseven.mztkbe.modules.marketplace.application.dto.RegisterClassResult;

/** HTTP response DTO for class registration. */
public record RegisterClassResponseDTO(Long classId) {

  public static RegisterClassResponseDTO from(RegisterClassResult result) {
    return new RegisterClassResponseDTO(result.classId());
  }
}
