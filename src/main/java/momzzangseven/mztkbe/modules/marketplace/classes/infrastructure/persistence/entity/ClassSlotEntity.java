package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;

/**
 * JPA Entity for the {@code class_slots} table.
 *
 * <p>Days of week are stored as a {@code @ElementCollection} in a join table {@code
 * class_slot_days}. Start time is stored as {@code LocalTime}.
 */
@Entity
@Table(name = "class_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ClassSlotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "class_id", nullable = false)
  private Long classId;

  @ElementCollection(fetch = FetchType.EAGER)
  @Enumerated(EnumType.STRING)
  @CollectionTable(
      name = "class_slot_days",
      joinColumns = @JoinColumn(name = "slot_id"),
      indexes = @Index(name = "idx_class_slot_days_slot_id", columnList = "slot_id"))
  @Column(name = "day_of_week")
  @org.hibernate.annotations.BatchSize(size = 30)
  private List<DayOfWeek> daysOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(nullable = false)
  private int capacity;

  @Column(nullable = false)
  private boolean active;

  // ============================================
  // Domain Mapping
  // ============================================

  /**
   * Convert a domain model to a JPA entity for persistence.
   *
   * @param slot the domain model to convert
   * @return the JPA entity
   */
  public static ClassSlotEntity fromDomain(ClassSlot slot) {
    return ClassSlotEntity.builder()
        .id(slot.getId())
        .classId(slot.getClassId())
        .daysOfWeek(slot.getDaysOfWeek())
        .startTime(slot.getStartTime())
        .capacity(slot.getCapacity())
        .active(slot.isActive())
        .build();
  }

  /**
   * Convert this entity to a domain model.
   *
   * @return the domain model
   */
  public ClassSlot toDomain() {
    return ClassSlot.builder()
        .id(this.id)
        .classId(this.classId)
        .daysOfWeek(this.daysOfWeek)
        .startTime(this.startTime)
        .capacity(this.capacity)
        .active(this.active)
        .build();
  }
}
