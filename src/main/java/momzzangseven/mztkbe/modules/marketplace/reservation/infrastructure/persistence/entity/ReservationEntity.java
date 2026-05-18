package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "class_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "trainer_id", nullable = false)
  private Long trainerId;

  @Column(name = "class_slot_id", nullable = false)
  private Long slotId;

  @Column(name = "reservation_date", nullable = false)
  private LocalDate reservationDate;

  @Column(name = "reservation_time", nullable = false)
  private LocalTime reservationTime;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReservationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "escrow_status", length = 40)
  private ReservationEscrowStatus escrowStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "escrow_flow", length = 30)
  private ReservationEscrowFlow escrowFlow;

  @Column(name = "user_request", length = 500)
  private String userRequest;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "order_id", length = 100)
  private String orderId;

  @Column(name = "order_key", length = 66)
  private String orderKey;

  @Column(name = "current_execution_intent_public_id", length = 36)
  private String currentExecutionIntentPublicId;

  @Column(name = "buyer_wallet_address", length = 42)
  private String buyerWalletAddress;

  @Column(name = "trainer_wallet_address", length = 42)
  private String trainerWalletAddress;

  @Column(name = "token_address", length = 42)
  private String tokenAddress;

  @Column(name = "price_base_units", length = 100)
  private String priceBaseUnits;

  @Column(name = "tx_hash", length = 100)
  private String txHash;

  @Column(name = "hold_expires_at")
  private LocalDateTime holdExpiresAt;

  @Column(name = "pending_action_expires_at")
  private LocalDateTime pendingActionExpiresAt;

  @Column(name = "expected_contract_deadline_epoch_seconds")
  private Long expectedContractDeadlineEpochSeconds;

  @Column(name = "expected_contract_deadline_at")
  private LocalDateTime expectedContractDeadlineAt;

  @Column(name = "contract_deadline_epoch_seconds")
  private Long contractDeadlineEpochSeconds;

  @Column(name = "contract_deadline_at")
  private LocalDateTime contractDeadlineAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "pending_action", length = 40)
  private ReservationEscrowAction pendingAction;

  @Column(name = "pending_attempt_token", length = 100)
  private String pendingAttemptToken;

  @Column(name = "pending_expected_version")
  private Long pendingExpectedVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "pending_expected_status", length = 30)
  private ReservationStatus pendingExpectedStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "pending_expected_escrow_status", length = 40)
  private ReservationEscrowStatus pendingExpectedEscrowStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "prior_status", length = 30)
  private ReservationStatus priorStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "prior_escrow_status", length = 40)
  private ReservationEscrowStatus priorEscrowStatus;

  @Column(name = "create_idempotency_key_hash", length = 128)
  private String createIdempotencyKeyHash;

  @Column(name = "create_payload_hash", length = 128)
  private String createPayloadHash;

  @Column(name = "server_signature_signed_at")
  private LocalDateTime serverSignatureSignedAt;

  @Column(name = "server_signature_expires_at")
  private LocalDateTime serverSignatureExpiresAt;

  @Column(name = "escrow_failure_code", length = 100)
  private String escrowFailureCode;

  @Column(name = "escrow_failure_message", length = 500)
  private String escrowFailureMessage;

  /** Snapshot of priceAmount at booking time. NULL for legacy records created before this field. */
  @Column(name = "booked_price_amount")
  private Integer bookedPriceAmount;

  /** Snapshot of class title at booking time. Null for legacy records created before this field. */
  @Column(name = "booked_class_title", length = 100)
  private String bookedClassTitle;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
