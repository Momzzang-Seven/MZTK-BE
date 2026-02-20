package momzzangseven.mztkbe.modules.location.api.dto;

import java.time.Instant;
import lombok.Builder;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationResult;

@Builder
public record DeleteLocationResponseDTO(Long locationId, String locationName, Instant deletedAt) {
  /**
   * Factory Method: Application Result → API Response
   *
   * @param result DeleteLocationResult
   * @return DeleteLocationResponseDTO
   */
  public static DeleteLocationResponseDTO from(DeleteLocationResult result) {
    return DeleteLocationResponseDTO.builder()
        .locationId(result.locationId())
        .locationName(result.locationName())
        .deletedAt(result.deletedAt())
        .build();
  }
}
