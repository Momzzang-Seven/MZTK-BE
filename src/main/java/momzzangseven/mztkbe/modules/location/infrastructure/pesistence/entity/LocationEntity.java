package momzzangseven.mztkbe.modules.location.infrastructure.pesistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;

@Entity
@Table(name = "locations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "location_name", nullable = false, length = 100)
  private String locationName;

  @Column(name = "postal_code", nullable = false, length = 10)
  private String postalCode;

  @Column(name = "address", nullable = false)
  private String address;

  @Column(name = "detail_address")
  private String detailAddress;

  @Column(name = "latitude", nullable = false)
  private Double latitude;

  @Column(name = "longitude", nullable = false)
  private Double longitude;

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    this.registeredAt = Instant.now();
    this.updatedAt = this.registeredAt;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /** Domain model -> Entity */
  public static LocationEntity from(Location location) {
    return LocationEntity.builder()
        .id(location.getId())
        .userId(location.getUserId())
        .locationName(location.getLocationName())
        .postalCode(location.getPostalCode())
        .address(location.getAddress())
        .detailAddress(location.getDetailAddress())
        .latitude(location.getCoordinate().getLatitude())
        .longitude(location.getCoordinate().getLongitude())
        .registeredAt(location.getRegisteredAt())
        .build();
  }

  /** Entity -> Domain model */
  public Location toDomain() {
    return Location.builder()
        .id(this.id)
        .userId(this.userId)
        .locationName(this.locationName)
        .postalCode(this.postalCode)
        .address(this.address)
        .detailAddress(this.detailAddress)
        .coordinate(new GpsCoordinate(this.latitude, this.longitude))
        .registeredAt(this.registeredAt)
        .build();
  }
}
