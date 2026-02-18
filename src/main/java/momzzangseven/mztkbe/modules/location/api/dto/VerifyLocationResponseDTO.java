package momzzangseven.mztkbe.modules.location.api.dto;

import java.time.Instant;
import lombok.Builder;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;

/** Verify Location Response DTO */
@Builder
public record VerifyLocationResponseDTO(
    Long verificationId,
    Long locationId,
    String locationName,
    Long userId,
    Boolean isVerified,
    Double distance,
    Double registeredLatitude,
    Double registeredLongitude,
    Double currentLatitude,
    Double currentLongitude,
    Instant verifiedAt,
    Boolean xpGranted,
    Integer grantedXp,
    String xpGrantMessage) {

  /** Factory Method: Application Result → API Response */
  public static VerifyLocationResponseDTO from(VerifyLocationResult result) {
    return VerifyLocationResponseDTO.builder()
        .verificationId(result.verificationId())
        .locationId(result.locationId())
        .locationName(result.locationName())
        .userId(result.userId())
        .isVerified(result.isVerified())
        .distance(result.distance())
        .registeredLatitude(result.registeredLatitude())
        .registeredLongitude(result.registeredLongitude())
        .currentLatitude(result.currentLatitude())
        .currentLongitude(result.currentLongitude())
        .verifiedAt(result.verifiedAt())
        .xpGranted(result.xpGranted())
        .grantedXp(result.grantedXp())
        .xpGrantMessage(result.xpGrantMessage())
        .build();
  }
}
