package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * JPA Entity for the {@code marketplace_classes} table.
 *
 * <h2>Lock strategy</h2>
 *
 * <ul>
 *   <li><b>Optimistic lock</b> ({@code @Version}): prevents lost metadata updates when two
 *       concurrent requests update the same class. JPA throws {@link
 *       jakarta.persistence.OptimisticLockException} on conflict, translated to {@link
 *       org.springframework.dao.OptimisticLockingFailureException}.
 * </ul>
 *
 * <h2>Tag storage</h2>
 *
 * <p>Tags are stored in the global {@code tags} + {@code class_tags} join table (same pattern as
 * posts). Features, having no global identity, are stored as a pipe-delimited text column.
 */
@Entity
@Table(name = "marketplace_classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MarketplaceClassEntity {

  private static final String DELIMITER = "|";
  private static final String DELIMITER_REGEX = "\\|";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "trainer_id", nullable = false)
  private Long trainerId;

  @Column(nullable = false, length = 100)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ClassCategory category;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "price_amount", nullable = false)
  private int priceAmount;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  /**
   * Optimistic lock version.
   *
   * <p>Prevents lost updates when two requests simultaneously update class metadata.
   */
  @Version
  @Column(nullable = false)
  private Long version;

  /**
   * Pipe-delimited features (max 10, max 100 chars each). Null when empty.
   *
   * <p>Unlike tags, features have no global identity and are stored inline.
   */
  @Column(name = "features", columnDefinition = "TEXT")
  private String features;

  @Column(name = "personal_items", columnDefinition = "TEXT")
  private String personalItems;

  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // ============================================
  // Domain Mapping
  // ============================================

  /**
   * Convert a domain model to a JPA entity.
   *
   * <p>Tags are deliberately NOT carried here — they are persisted separately via {@link
   * momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter.ClassTagAdapter}.
   *
   * @param mc the domain model to convert
   * @return the JPA entity
   */
  public static MarketplaceClassEntity fromDomain(MarketplaceClass mc) {
    return MarketplaceClassEntity.builder()
        .id(mc.getId())
        .trainerId(mc.getTrainerId())
        .title(mc.getTitle())
        .category(mc.getCategory())
        .description(mc.getDescription())
        .priceAmount(mc.getPriceAmount())
        .durationMinutes(mc.getDurationMinutes())
        .version(mc.getVersion())
        .features(encode(mc.getFeatures()))
        .personalItems(mc.getPersonalItems())
        .active(mc.isActive())
        .build();
  }

  /**
   * Convert this entity to a domain model.
   *
   * <p>Tags are NOT included — the persistence adapter enriches the domain model separately after
   * loading tags from the {@code class_tags} join table.
   *
   * @return the domain model (with empty tags list)
   */
  public MarketplaceClass toDomain() {
    return toDomainWithTags(List.of());
  }

  /**
   * Convert this entity to a domain model with the supplied tag name list.
   *
   * <p>Called by the persistence adapter after it has loaded tags from {@code class_tags}.
   *
   * @param tags tag name list
   * @return the domain model
   */
  public MarketplaceClass toDomainWithTags(List<String> tags) {
    return MarketplaceClass.builder()
        .id(this.id)
        .trainerId(this.trainerId)
        .title(this.title)
        .category(this.category)
        .description(this.description)
        .priceAmount(this.priceAmount)
        .durationMinutes(this.durationMinutes)
        .version(this.version)
        .tags(tags)
        .features(decode(this.features))
        .personalItems(this.personalItems)
        .active(this.active)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }

  // ============================================
  // Serialization helpers
  // ============================================

  private static String encode(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return String.join(DELIMITER, list);
  }

  private static List<String> decode(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(DELIMITER_REGEX))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }
}
