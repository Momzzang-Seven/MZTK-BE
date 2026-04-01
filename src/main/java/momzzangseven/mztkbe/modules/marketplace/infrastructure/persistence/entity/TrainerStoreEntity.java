package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * JPA Entity for trainer_stores table.
 *
 * <p><b>Timestamp management:</b> {@code createdAt} and {@code updatedAt} are managed exclusively
 * by the native upsert query using {@code CURRENT_TIMESTAMP}. JPA Auditing annotations are
 * intentionally omitted because the entity is never persisted through JPA's standard persist/merge
 * lifecycle.
 */
@Entity
@Table(name = "trainer_stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TrainerStoreEntity {

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, unique = true)
  private Long trainerId;

  @Column(name = "store_name")
  private String storeName;

  @Column(nullable = false)
  private String address;

  @Column(name = "detail_address")
  private String detailAddress;

  @Column(name = "location", columnDefinition = "geometry(Point,4326)")
  private Point location;

  @Column(name = "phone_number")
  private String phoneNumber;

  @Column(name = "homepage_url")
  private String homepageUrl;

  @Column(name = "instagram_url")
  private String instagramUrl;

  @Column(name = "x_url")
  private String xUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // ============================================
  // Derived Getters (from Point location)
  // ============================================

  public Double getLatitude() {
    return location != null ? location.getY() : null;
  }

  public Double getLongitude() {
    return location != null ? location.getX() : null;
  }

  // ============================================
  // Domain Mapping
  // ============================================

  /**
   * Convert a domain model to an entity for persistence.
   *
   * <p><b>Note on id field:</b> When called from the save path (via {@code TrainerStore.create()}),
   * {@code store.getId()} is always null because the domain factory does not assign IDs. The native
   * upsert query ignores the id column entirely (uses IDENTITY generation). The id is included here
   * only for completeness, supporting potential future use cases where an entity is reconstructed
   * from a database-loaded domain model.
   *
   * @param store the domain model to convert
   * @return the JPA entity
   */
  public static TrainerStoreEntity fromDomain(TrainerStore store) {
    Point point = null;
    if (store.getLongitude() != null && store.getLatitude() != null) {
      point =
          GEOMETRY_FACTORY.createPoint(new Coordinate(store.getLongitude(), store.getLatitude()));
      point.setSRID(4326);
    }

    return TrainerStoreEntity.builder()
        .id(store.getId())
        .trainerId(store.getTrainerId())
        .storeName(store.getStoreName())
        .address(store.getAddress())
        .detailAddress(store.getDetailAddress())
        .location(point)
        .phoneNumber(store.getPhoneNumber())
        .homepageUrl(store.getHomepageUrl())
        .instagramUrl(store.getInstagramUrl())
        .xUrl(store.getXUrl())
        .createdAt(store.getCreatedAt())
        .updatedAt(store.getUpdatedAt())
        .build();
  }

  /**
   * Convert this entity to a domain model.
   *
   * @return the domain model
   */
  public TrainerStore toDomain() {
    return TrainerStore.builder()
        .id(this.id)
        .trainerId(this.trainerId)
        .storeName(this.storeName)
        .address(this.address)
        .detailAddress(this.detailAddress)
        .latitude(this.getLatitude())
        .longitude(this.getLongitude())
        .phoneNumber(this.phoneNumber)
        .homepageUrl(this.homepageUrl)
        .instagramUrl(this.instagramUrl)
        .xUrl(this.xUrl)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }
}
