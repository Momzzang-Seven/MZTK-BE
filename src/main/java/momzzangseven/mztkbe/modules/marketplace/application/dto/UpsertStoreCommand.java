package momzzangseven.mztkbe.modules.marketplace.application.dto;

/**
 * Command for creating or updating a trainer store.
 *
 * <p>Validates business rules in the {@link #validate()} method, which must be called by the
 * service before executing business logic.
 *
 * @param trainerId trainer's user ID (from authentication)
 * @param storeName store name
 * @param address store address
 * @param detailAddress detailed address
 * @param latitude latitude coordinate
 * @param longitude longitude coordinate
 * @param phoneNumber phone number
 * @param homepageUrl homepage URL
 * @param instagramUrl Instagram URL
 * @param xProfileUrl X (Twitter) profile URL
 */
public record UpsertStoreCommand(
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

  /** Validate command fields before executing business logic. */
  public void validate() {
    if (trainerId == null || trainerId <= 0) {
      throw new IllegalArgumentException("Trainer ID must be a positive number");
    }
    if (storeName == null || storeName.isBlank()) {
      throw new IllegalArgumentException("Store name must not be blank");
    }
    if (address == null || address.isBlank()) {
      throw new IllegalArgumentException("Address must not be blank");
    }
    if (detailAddress == null || detailAddress.isBlank()) {
      throw new IllegalArgumentException("Detail address must not be blank");
    }
    if (latitude == null) {
      throw new IllegalArgumentException("Latitude must not be null");
    }
    if (longitude == null) {
      throw new IllegalArgumentException("Longitude must not be null");
    }
    if (phoneNumber == null || phoneNumber.isBlank()) {
      throw new IllegalArgumentException("Phone number must not be blank");
    }
  }
}
