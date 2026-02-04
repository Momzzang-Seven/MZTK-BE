package momzzangseven.mztkbe.modules.location.application.dto;

import lombok.Builder;

/**
 * DTO for result of reverse geocoding(GPS Coordinates -> address)
 *
 * @param address
 * @param postalCode
 */
@Builder
public record AddressInfo(String address, String postalCode) {
  public static AddressInfo of(String address, String postalCode) {
    return AddressInfo.builder().address(address).postalCode(postalCode).build();
  }
}
