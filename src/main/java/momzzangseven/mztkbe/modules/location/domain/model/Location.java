package momzzangseven.mztkbe.modules.location.domain.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.location.domain.vo.AddressData;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;

/** Domain model for location - contains business logic - independent of infrastructure layer */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Location {
  private Long id;
  private Long userId;
  private String locationName;
  private String postalCode;
  private String address;
  private String detailAddress;
  private GpsCoordinate coordinate;
  private Instant registeredAt;

  /** Factory method */
  public static Location create(
      Long userId, String locationName, GpsCoordinate coordinate, AddressData addressData) {
    return Location.builder()
        .userId(userId)
        .locationName(locationName)
        .postalCode(addressData.getPostalCode())
        .address(addressData.getAddress())
        .detailAddress(addressData.getDetailedAddress())
        .coordinate(coordinate)
        .registeredAt(Instant.now())
        .build();
  }

  /** Confirm is this location is owned by specific user by user id */
  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  /** Calculate distance with specific GPS coordinate */
  public double calculateDistanceFrom(GpsCoordinate otherCoordinate) {
    return this.coordinate.distanceTo(otherCoordinate);
  }
}
