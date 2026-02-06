package momzzangseven.mztkbe.modules.location.domain.vo;

/**
 * Address Data Value Object
 *
 * <p>Immutable Value Object representing address information.
 *
 * @param address Road name address or Jibun address (required)
 * @param postalCode Postal code (not null, empty string allowed)
 * @param detailedAddress Detailed address (optional, street/house number, etc.)
 */
public record AddressData(String address, String postalCode, String detailedAddress) {

  /**
   * Compact Constructor - Validation and normalization
   *
   * <p>Validation Rules:
   *
   * <ul>
   *   <li>address: required (null or blank string not allowed)
   *   <li>postalCode: not null (empty string allowed when reverse geocoding, DB NOT NULL)
   *   <li>detailedAddress: optional (null allowed, null will be normalized to empty string)
   * </ul>
   */
  public AddressData {
    // address is required (null or blank string not allowed)
    if (address == null || address.isBlank()) {
      throw new IllegalArgumentException("Address cannot be null or blank");
    }

    // postalCode is not null (DB NOT NULL), normalize to empty string
    postalCode = (postalCode == null) ? "" : postalCode;

    // detailedAddress is null, normalize to empty string (DB consistency)
    detailedAddress = (detailedAddress == null) ? "" : detailedAddress;
  }
}
