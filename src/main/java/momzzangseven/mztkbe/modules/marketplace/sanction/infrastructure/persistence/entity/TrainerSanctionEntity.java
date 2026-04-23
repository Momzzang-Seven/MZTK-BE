package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "trainer_sanctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
public class TrainerSanctionEntity {

  @Id
  @Column(name = "trainer_id", nullable = false)
  private Long trainerId;

  @Column(name = "strike_count", nullable = false)
  @Builder.Default
  private int strikeCount = 0;

  @Column(name = "suspended_until")
  private LocalDateTime suspendedUntil;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * Increment the strike count and activate a 7-day suspension every 3rd strike.
   *
   * @param now current time (injected from Clock for testability)
   * @return new entity with updated strike count and optional suspension
   */
  public TrainerSanctionEntity addStrike(LocalDateTime now) {
    int newCount = this.strikeCount + 1;
    LocalDateTime newSuspendedUntil = this.suspendedUntil;

    // Every 3rd strike (3, 6, 9, …) triggers a 7-day suspension
    if (newCount % 3 == 0) {
      newSuspendedUntil = now.plusDays(7);
    }

    return this.toBuilder()
        .strikeCount(newCount)
        .suspendedUntil(newSuspendedUntil)
        .build();
  }
}
