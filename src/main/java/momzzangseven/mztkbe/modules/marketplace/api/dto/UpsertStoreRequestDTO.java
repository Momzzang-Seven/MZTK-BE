package momzzangseven.mztkbe.modules.marketplace.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreCommand;
import org.hibernate.validator.constraints.URL;

/**
 * Request DTO for creating or updating a trainer store.
 *
 * @param storeName store name (required)
 * @param address store address (required)
 * @param detailAddress detailed address (optional)
 * @param latitude latitude coordinate (-90.0 ~ 90.0, required)
 * @param longitude longitude coordinate (-180.0 ~ 180.0, required)
 * @param phoneNumber phone number (optional)
 * @param homepageUrl homepage URL (optional, must be valid URL if provided)
 * @param instagramUrl Instagram URL (optional, must be valid URL if provided)
 * @param xUrl X (Twitter) URL (optional, must be valid URL if provided)
 */
public record UpsertStoreRequestDTO(
    @NotBlank(message = "Store name is required") String storeName,
    @NotBlank(message = "Address is required") String address,
    String detailAddress,
    @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
        Double latitude,
    @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
        Double longitude,
    String phoneNumber,
    @URL(message = "Homepage URL must be a valid URL") String homepageUrl,
    @URL(message = "Instagram URL must be a valid URL") String instagramUrl,
    @URL(message = "X URL must be a valid URL") String xUrl) {

  /**
   * Convert this request DTO to an application-layer command.
   *
   * <p>Centralizes the DTO→Command mapping in a single place, eliminating error-prone positional
   * field mapping in the controller.
   *
   * @param trainerId authenticated trainer's user ID
   * @return UpsertStoreCommand ready for the command handler
   */
  public UpsertStoreCommand toCommand(Long trainerId) {
    return new UpsertStoreCommand(
        trainerId,
        this.storeName,
        this.address,
        this.detailAddress,
        this.latitude,
        this.longitude,
        this.phoneNumber,
        this.homepageUrl,
        this.instagramUrl,
        this.xUrl);
  }
}
