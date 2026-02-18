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
@Builder(toBuilder = true)
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
  private Instant deletedAt;

  /** Factory method */
  public static Location create(
      Long userId, String locationName, GpsCoordinate coordinate, AddressData addressData) {
    return Location.builder()
        .userId(userId)
        .locationName(locationName)
        .postalCode(addressData.postalCode())
        .address(addressData.address())
        .detailAddress(addressData.detailedAddress())
        .coordinate(coordinate)
        .registeredAt(Instant.now())
        .build();
  }

  /** Confirm is this location is owned by specific user by user id */
  public boolean isOwnedBy(Long userId) {
    if (userId == null || this.userId == null) {
      return false;
    }
    return this.userId.equals(userId);
  }

  /** Calculate distance with specific GPS coordinate */
  public double calculateDistanceFrom(GpsCoordinate otherCoordinate) {
    return this.coordinate.distanceTo(otherCoordinate);
  }

  /**
   * Check whether this location is deleted or not. This method only used when the user is
   * soft-deleted.
   *
   * @return true if this location is soft-deleted
   */
  public boolean isDeleted() {
    return this.deletedAt != null;
  }

  /**
   * Mark this location is soft-deleted.
   *
   * @return Location object set deletedAt is now.
   */
  public Location markAsDeleted() {
    return this.toBuilder().deletedAt(Instant.now()).build();
  }
}
