package momzzangseven.mztkbe.modules.location.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Verify Location Request DTO
 *
 * @param locationId Location ID to verify
 * @param currentLatitude Current latitude
 * @param currentLongitude Current longitude
 */
public record VerifyLocationRequestDTO(
    @NotNull(message = "Location ID is required") Long locationId,
    @NotNull(message = "Current latitude is required") Double currentLatitude,
    @NotNull(message = "Current longitude is required") Double currentLongitude) {
  // Validation is handled by @NotNull
  // GPS coordinate range validation is performed in the GpsCoordinate constructor
}
