package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(
    name = "marketplace_reservation_action_states",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_marketplace_reservation_action_states_token",
          columnNames = "attempt_token"),
      @UniqueConstraint(
          name = "uk_marketplace_reservation_action_states_intent",
          columnNames = "execution_intent_public_id"),
      @UniqueConstraint(
          name = "uk_marketplace_reservation_action_states_attempt",
          columnNames = {"reservation_id", "attempt_no"}),
      @UniqueConstraint(
          name = "uk_marketplace_reservation_action_states_graph",
          columnNames = {"id", "reservation_id", "escrow_id"})
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketplaceReservationActionStateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reservation_id", nullable = false)
  private Long reservationId;

  @Column(name = "escrow_id", nullable = false)
  private Long escrowId;

  @Column(name = "action_type", nullable = false, length = 40)
  private String actionType;

  @Column(name = "actor_type", nullable = false, length = 20)
  private String actorType;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "request_source", nullable = false, length = 30)
  private String requestSource;

  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo;

  @Column(name = "attempt_token", nullable = false, length = 100)
  private String attemptToken;

  @Column(name = "execution_intent_public_id", length = 36)
  private String executionIntentPublicId;

  @Column(name = "root_idempotency_key", length = 250)
  private String rootIdempotencyKey;

  @Column(name = "payload_hash", length = 66)
  private String payloadHash;

  @Column(name = "status", nullable = false, length = 40)
  private String status;

  @Column(name = "expected_reservation_version")
  private Long expectedReservationVersion;

  @Column(name = "expected_reservation_status", length = 30)
  private String expectedReservationStatus;

  @Column(name = "expected_escrow_status", length = 40)
  private String expectedEscrowStatus;

  @Column(name = "prior_reservation_status", length = 30)
  private String priorReservationStatus;

  @Column(name = "prior_escrow_status", length = 40)
  private String priorEscrowStatus;

  @Column(name = "preparation_expires_at")
  private LocalDateTime preparationExpiresAt;

  @Column(name = "server_signature_signed_at")
  private LocalDateTime serverSignatureSignedAt;

  @Column(name = "server_signature_expires_at")
  private LocalDateTime serverSignatureExpiresAt;

  @Column(name = "action_reason", length = 500)
  private String actionReason;

  @Column(name = "reason_code", length = 80)
  private String reasonCode;

  @Column(name = "memo", length = 500)
  private String memo;

  @Column(name = "retryable")
  private Boolean retryable;

  @Column(name = "error_code", length = 120)
  private String errorCode;

  @Column(name = "error_reason", length = 500)
  private String errorReason;

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
