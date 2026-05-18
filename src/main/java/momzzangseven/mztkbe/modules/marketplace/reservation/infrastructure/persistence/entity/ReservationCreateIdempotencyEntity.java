package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;

@Entity
@Table(
    name = "reservation_create_idempotency_keys",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_reservation_create_idempotency_buyer_key",
            columnNames = {"buyer_id", "key_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReservationCreateIdempotencyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buyer_id", nullable = false)
  private Long buyerId;

  @Column(name = "key_hash", nullable = false, length = 128)
  private String keyHash;

  @Column(name = "payload_hash", nullable = false, length = 128)
  private String payloadHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReservationCreateIdempotencyStatus status;

  @Column(name = "reservation_id")
  private Long reservationId;

  @Column(name = "current_execution_intent_public_id", length = 36)
  private String currentExecutionIntentPublicId;

  @Column(name = "response_snapshot_json", columnDefinition = "TEXT")
  private String responseSnapshotJson;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
