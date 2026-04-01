package momzzangseven.mztkbe.modules.marketplace.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Domain model representing a trainer's store (marketplace).
 *
 * <p>Current version supports only create/replace semantics via native upsert. Partial update
 * methods (e.g., deactivate, changeBusinessHours) should be added here as business requirements
 * emerge, NOT in the service layer.
 */
@Getter
@Builder
public class TrainerStore {
  private Long id;
  private Long trainerId;
  private String storeName;
  private String address;
  private String detailAddress;
  private Double latitude;
  private Double longitude;
  private String phoneNumber;
  private String homepageUrl;
  private String instagramUrl;
  private String xUrl;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // ============================================
  // Factory Methods
  // ============================================

  /**
   * Create a new TrainerStore.
   *
   * @param trainerId trainer's user ID
   * @param storeName store name
   * @param address store address
   * @param detailAddress detailed address
   * @param latitude latitude coordinate
   * @param longitude longitude coordinate
   * @param phoneNumber phone number
   * @param homepageUrl homepage URL
   * @param instagramUrl Instagram URL
   * @param xUrl X (Twitter) URL
   * @return new TrainerStore instance
   */
  public static TrainerStore create(
      Long trainerId,
      String storeName,
      String address,
      String detailAddress,
      Double latitude,
      Double longitude,
      String phoneNumber,
      String homepageUrl,
      String instagramUrl,
      String xUrl) {

    validateTrainerId(trainerId);
    validateStoreName(storeName);
    validateAddress(address);
    validateCoordinates(latitude, longitude);
    validateUrl("Homepage URL", homepageUrl);
    validateUrl("Instagram URL", instagramUrl);
    validateUrl("X URL", xUrl);

    // createdAt/updatedAt are NOT set here.
    // The native upsert query uses CURRENT_TIMESTAMP as the single source of truth.
    // These fields are only populated when reading back from the database.
    return TrainerStore.builder()
        .trainerId(trainerId)
        .storeName(storeName)
        .address(address)
        .detailAddress(detailAddress)
        .latitude(latitude)
        .longitude(longitude)
        .phoneNumber(phoneNumber)
        .homepageUrl(homepageUrl)
        .instagramUrl(instagramUrl)
        .xUrl(xUrl)
        .build();
  }

  // ============================================
  // Validation Methods
  // ============================================

  private static void validateTrainerId(Long trainerId) {
    if (trainerId == null || trainerId <= 0) {
      throw new IllegalArgumentException("Trainer ID must be a positive number");
    }
  }

  private static void validateStoreName(String storeName) {
    if (storeName == null || storeName.isBlank()) {
      throw new IllegalArgumentException("Store name must not be null or blank");
    }
  }

  private static void validateAddress(String address) {
    if (address == null || address.isBlank()) {
      throw new IllegalArgumentException("Address must not be null or blank");
    }
  }

  private static void validateCoordinates(Double latitude, Double longitude) {
    if (latitude == null || latitude < -90.0 || latitude > 90.0) {
      throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0");
    }
    if (longitude == null || longitude < -180.0 || longitude > 180.0) {
      throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0");
    }
  }

  /**
   * Validates that a URL, if provided, uses http or https scheme and is well-formed.
   *
   * @param fieldName human-readable field name for error messages
   * @param url the URL to validate (null or blank values are accepted as optional)
   */
  private static void validateUrl(String fieldName, String url) {
    if (url == null || url.isBlank()) {
      return;
    }
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      if (!"https".equals(scheme) && !"http".equals(scheme)) {
        throw new IllegalArgumentException(fieldName + " must use http or https scheme");
      }
      uri.toURL(); // validates well-formedness
    } catch (IllegalArgumentException e) {
      throw e; // re-throw our own validation exception
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(
          fieldName + " must be a valid URL: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          fieldName + " must be a valid URL: " + e.getMessage(), e);
    }
  }
}
