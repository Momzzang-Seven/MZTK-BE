package momzzangseven.mztkbe.modules.marketplace.store.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.UpsertStoreCommand;
import org.hibernate.validator.constraints.URL;

/**
 * Request DTO for creating or updating a trainer store.
 *
 * <p>Optional string fields are normalized: blank values are converted to {@code null} during
 * command mapping to prevent empty strings from leaking into the domain layer.
 *
 * @param storeName store name (required, max 100)
 * @param address store address (required, max 255)
 * @param detailAddress detailed address (required, max 255)
 * @param latitude latitude coordinate (-90.0 ~ 90.0, required)
 * @param longitude longitude coordinate (-180.0 ~ 180.0, required)
 * @param phoneNumber phone number (required, international format, max 20)
 * @param homepageUrl homepage URL (optional, must be valid URL if provided, max 500)
 * @param instagramUrl Instagram URL (optional, must be valid URL if provided, max 500)
 * @param xProfileUrl X (Twitter) profile URL (optional, must be valid URL if provided, max 500)
 */
public record UpsertStoreRequestDTO(
    @NotBlank(message = "Store name is required")
        @Size(max = 100, message = "Store name must not exceed 100 characters")
        String storeName,
    @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address,
    @NotBlank(message = "Detail address is required")
        @Size(max = 255, message = "Detail address must not exceed 255 characters")
        String detailAddress,
    @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
        Double latitude,
    @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
        Double longitude,
    @NotBlank(message = "Phone number is required")
        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        @Pattern(
            regexp = "^\\+?\\d[\\d \\-]{6,14}\\d$",
            message =
                "Phone number must be a valid format (e.g., 010-1234-5678 or +82-10-1234-5678)")
        String phoneNumber,
    @URL(message = "Homepage URL must be a valid URL")
        @Size(max = 500, message = "Homepage URL must not exceed 500 characters")
        String homepageUrl,
    @URL(message = "Instagram URL must be a valid URL")
        @Size(max = 500, message = "Instagram URL must not exceed 500 characters")
        String instagramUrl,
    @URL(message = "X Profile URL must be a valid URL")
        @Size(max = 500, message = "X Profile URL must not exceed 500 characters")
        String xProfileUrl) {

  /**
   * Convert this request DTO to an application-layer command.
   *
   * <p>Centralizes the DTO→Command mapping in a single place, eliminating error-prone positional
   * field mapping in the controller. Optional string fields are normalized: blank values are
   * converted to {@code null}.
   *
   * @param trainerId authenticated trainer's user ID
   * @return UpsertStoreCommand ready for the use case
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
        blankToNull(this.homepageUrl),
        blankToNull(this.instagramUrl),
        blankToNull(this.xProfileUrl));
  }

  /**
   * Normalize blank strings to null for optional fields.
   *
   * @param value the string value to normalize
   * @return null if blank, otherwise the original value
   */
  private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }
}
