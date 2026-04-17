package momzzangseven.mztkbe.modules.marketplace.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCoordinatesException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidPhoneNumberException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidStoreAddressException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidStoreNameException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidStoreUrlException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTrainerIdException;

/**
 * Domain model representing a trainer's store (marketplace).
 *
 * <p>All fields are {@code private final} to enforce immutability. New instances are created
 * exclusively via {@link #create} (for new stores) or via {@link #update} (for modifications). Both
 * methods enforce all domain invariants.
 *
 * <p>Additional mutation methods (e.g., deactivate, changeBusinessHours) should be added here as
 * business requirements emerge, NOT in the service layer.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TrainerStore {

  // ============================================
  // Field Length Constants
  // ============================================

  public static final int MAX_STORE_NAME_LENGTH = 100;
  public static final int MAX_ADDRESS_LENGTH = 255;
  public static final int MAX_DETAIL_ADDRESS_LENGTH = 255;
  public static final int MAX_PHONE_NUMBER_LENGTH = 20;
  public static final int MAX_URL_LENGTH = 500;

  /**
   * Phone number pattern — international format supported.
   *
   * <p>Allows optional leading '+', digits, spaces, and hyphens. Length between 8 and 16 total
   * digits.
   */
  public static final String PHONE_NUMBER_PATTERN = "^\\+?\\d[\\d \\-]{6,14}\\d$";

  // ============================================
  // Fields (all private final for immutability)
  // ============================================

  private final Long id;
  private final Long trainerId;
  private final String storeName;
  private final String address;
  private final String detailAddress;
  private final Double latitude;
  private final Double longitude;
  private final String phoneNumber;
  private final String homepageUrl;
  private final String instagramUrl;
  private final String xProfileUrl;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  // ============================================
  // Factory Methods
  // ============================================

  /**
   * Create a new TrainerStore.
   *
   * @param trainerId trainer's user ID
   * @param storeName store name
   * @param address store address
   * @param detailAddress detailed address (required)
   * @param latitude latitude coordinate
   * @param longitude longitude coordinate
   * @param phoneNumber phone number (required, international format)
   * @param homepageUrl homepage URL
   * @param instagramUrl Instagram URL
   * @param xProfileUrl X (Twitter) profile URL
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
      String xProfileUrl) {

    validateTrainerId(trainerId);
    validateStoreName(storeName);
    validateAddress(address);
    validateDetailAddress(detailAddress);
    validateCoordinates(latitude, longitude);
    validatePhoneNumber(phoneNumber);
    validateUrl("Homepage URL", homepageUrl, MAX_URL_LENGTH);
    validateUrl("Instagram URL", instagramUrl, MAX_URL_LENGTH);
    validateUrl("X Profile URL", xProfileUrl, MAX_URL_LENGTH);

    // createdAt/updatedAt are NOT set here.
    // They are managed by Hibernate's @CreationTimestamp and @UpdateTimestamp on the entity.
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
        .xProfileUrl(xProfileUrl)
        .build();
  }

  /**
   * Update this store with new values while preserving identity (id, trainerId, createdAt).
   *
   * <p>All domain validations are re-applied to ensure business invariants are never bypassed.
   *
   * @param storeName new store name
   * @param address new address
   * @param detailAddress new detail address
   * @param latitude new latitude
   * @param longitude new longitude
   * @param phoneNumber new phone number
   * @param homepageUrl new homepage URL
   * @param instagramUrl new Instagram URL
   * @param xProfileUrl new X (Twitter) profile URL
   * @return new TrainerStore instance with updated values and preserved identity
   */
  public TrainerStore update(
      String storeName,
      String address,
      String detailAddress,
      Double latitude,
      Double longitude,
      String phoneNumber,
      String homepageUrl,
      String instagramUrl,
      String xProfileUrl) {

    validateStoreName(storeName);
    validateAddress(address);
    validateDetailAddress(detailAddress);
    validateCoordinates(latitude, longitude);
    validatePhoneNumber(phoneNumber);
    validateUrl("Homepage URL", homepageUrl, MAX_URL_LENGTH);
    validateUrl("Instagram URL", instagramUrl, MAX_URL_LENGTH);
    validateUrl("X Profile URL", xProfileUrl, MAX_URL_LENGTH);

    return toBuilder()
        .storeName(storeName)
        .address(address)
        .detailAddress(detailAddress)
        .latitude(latitude)
        .longitude(longitude)
        .phoneNumber(phoneNumber)
        .homepageUrl(homepageUrl)
        .instagramUrl(instagramUrl)
        .xProfileUrl(xProfileUrl)
        .build();
  }

  // ============================================
  // Validation Methods
  // ============================================

  private static void validateTrainerId(Long trainerId) {
    if (trainerId == null || trainerId <= 0) {
      throw new MarketplaceInvalidTrainerIdException();
    }
  }

  private static void validateStoreName(String storeName) {
    if (storeName == null || storeName.isBlank()) {
      throw new MarketplaceInvalidStoreNameException("Store name must not be null or blank");
    }
    if (storeName.length() > MAX_STORE_NAME_LENGTH) {
      throw new MarketplaceInvalidStoreNameException(
          "Store name must not exceed " + MAX_STORE_NAME_LENGTH + " characters");
    }
  }

  private static void validateAddress(String address) {
    if (address == null || address.isBlank()) {
      throw new MarketplaceInvalidStoreAddressException("Address must not be null or blank");
    }
    if (address.length() > MAX_ADDRESS_LENGTH) {
      throw new MarketplaceInvalidStoreAddressException(
          "Address must not exceed " + MAX_ADDRESS_LENGTH + " characters");
    }
  }

  private static void validateDetailAddress(String detailAddress) {
    if (detailAddress == null || detailAddress.isBlank()) {
      throw new MarketplaceInvalidStoreAddressException(
          "Detail address must not be null or blank");
    }
    if (detailAddress.length() > MAX_DETAIL_ADDRESS_LENGTH) {
      throw new MarketplaceInvalidStoreAddressException(
          "Detail address must not exceed " + MAX_DETAIL_ADDRESS_LENGTH + " characters");
    }
  }

  private static void validateCoordinates(Double latitude, Double longitude) {
    if (latitude == null || latitude < -90.0 || latitude > 90.0) {
      throw new MarketplaceInvalidCoordinatesException(
          "Latitude must be between -90.0 and 90.0");
    }
    if (longitude == null || longitude < -180.0 || longitude > 180.0) {
      throw new MarketplaceInvalidCoordinatesException(
          "Longitude must be between -180.0 and 180.0");
    }
  }

  /**
   * Validates that a phone number is provided and matches the allowed format.
   *
   * @param phoneNumber the phone number to validate (required)
   */
  private static void validatePhoneNumber(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.isBlank()) {
      throw new MarketplaceInvalidPhoneNumberException(
          "Phone number must not be null or blank");
    }
    if (phoneNumber.length() > MAX_PHONE_NUMBER_LENGTH) {
      throw new MarketplaceInvalidPhoneNumberException(
          "Phone number must not exceed " + MAX_PHONE_NUMBER_LENGTH + " characters");
    }
    if (!phoneNumber.matches(PHONE_NUMBER_PATTERN)) {
      throw new MarketplaceInvalidPhoneNumberException(
          "Phone number must be a valid format (e.g., 010-1234-5678 or +82-10-1234-5678)");
    }
  }

  /**
   * Validates that a URL, if provided, uses http or https scheme, is well-formed, and does not
   * exceed the maximum length.
   *
   * @param fieldName human-readable field name for error messages
   * @param url the URL to validate (null values are accepted as optional)
   * @param maxLength maximum allowed length
   */
  private static void validateUrl(String fieldName, String url, int maxLength) {
    if (url == null) {
      return;
    }
    if (url.length() > maxLength) {
      throw new MarketplaceInvalidStoreUrlException(
          fieldName + " must not exceed " + maxLength + " characters");
    }
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      if (!"https".equals(scheme) && !"http".equals(scheme)) {
        throw new MarketplaceInvalidStoreUrlException(
            fieldName + " must use http or https scheme");
      }
      uri.toURL(); // validates well-formedness
    } catch (MarketplaceInvalidStoreUrlException e) {
      throw e; // re-throw our own validation exception
    } catch (URISyntaxException e) {
      throw new MarketplaceInvalidStoreUrlException(
          fieldName + " must be a valid URL: " + e.getMessage());
    } catch (Exception e) {
      throw new MarketplaceInvalidStoreUrlException(
          fieldName + " must be a valid URL: " + e.getMessage());
    }
  }
}
