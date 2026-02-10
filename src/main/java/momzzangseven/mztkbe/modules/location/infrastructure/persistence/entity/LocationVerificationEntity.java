package momzzangseven.mztkbe.modules.location.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;

/**
 * Location Verification Entity
 *
 * <p>Location verification record entity. Save all verification attempts as audit logs.
 */
@Entity
@Table(
    name = "location_verifications",
    indexes = {
      @Index(name = "idx_user_id", columnList = "user_id"),
      @Index(name = "idx_location_id", columnList = "location_id"),
      @Index(name = "idx_verified_at", columnList = "verified_at DESC"),
      @Index(name = "idx_user_verified", columnList = "user_id, verified_at DESC")
    })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationVerificationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "location_id", nullable = true) // nullable: location deleted = NULL
  private Long locationId;

  @Column(name = "location_name", nullable = false, length = 100)
  private String locationName; // denormalization: keep deleted location information

  @Column(name = "is_verified", nullable = false)
  private Boolean isVerified;

  @Column(name = "distance", nullable = false)
  private Double distance; // meter unit (double precision type)

  @Column(name = "registered_latitude", nullable = false)
  private Double registeredLatitude;

  @Column(name = "registered_longitude", nullable = false)
  private Double registeredLongitude;

  @Column(name = "current_latitude", nullable = false)
  private Double currentLatitude;

  @Column(name = "current_longitude", nullable = false)
  private Double currentLongitude;

  @Column(name = "verified_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
  private Instant verifiedAt;

  /** Entity → Domain Model conversion */
  public LocationVerification toDomain() {
    return LocationVerification.builder()
        .id(this.id)
        .userId(this.userId)
        .locationId(this.locationId)
        .locationName(this.locationName)
        .isVerified(this.isVerified)
        .distance(this.distance)
        .registeredCoordinate(new GpsCoordinate(this.registeredLatitude, this.registeredLongitude))
        .currentCoordinate(new GpsCoordinate(this.currentLatitude, this.currentLongitude))
        .verifiedAt(this.verifiedAt)
        .build();
  }

  /** Domain Model → Entity conversion */
  public static LocationVerificationEntity fromDomain(LocationVerification verification) {
    return LocationVerificationEntity.builder()
        .id(verification.getId())
        .userId(verification.getUserId())
        .locationId(verification.getLocationId())
        .locationName(verification.getLocationName())
        .isVerified(verification.isVerified())
        .distance(verification.getDistance())
        .registeredLatitude(verification.getRegisteredCoordinate().latitude())
        .registeredLongitude(verification.getRegisteredCoordinate().longitude())
        .currentLatitude(verification.getCurrentCoordinate().latitude())
        .currentLongitude(verification.getCurrentCoordinate().longitude())
        .verifiedAt(verification.getVerifiedAt())
        .build();
  }
}
