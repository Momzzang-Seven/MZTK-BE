package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceAttemptStatus;

@Entity
@Table(
    name = "web3_nonce_slot_attempts",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_web3_nonce_slot_attempt_no",
          columnNames = {"chain_id", "from_address", "nonce", "attempt_no"}),
      @UniqueConstraint(name = "uk_web3_nonce_slot_attempt_idem", columnNames = "idempotency_key"),
      @UniqueConstraint(name = "uk_web3_nonce_slot_attempt_tx", columnNames = "tx_id")
    },
    indexes = {
      @Index(
          name = "idx_web3_nonce_slot_attempt_scope_created",
          columnList = "chain_id,from_address,nonce,created_at")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NonceSlotAttemptEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "chain_id", nullable = false)
  private Long chainId;

  @Column(name = "from_address", nullable = false, length = 42)
  private String fromAddress;

  @Column(name = "nonce", nullable = false)
  private Long nonce;

  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo;

  @Column(name = "tx_id", nullable = false)
  private Long txId;

  @Column(name = "tx_hash", length = 66)
  private String txHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private SponsorNonceAttemptStatus status;

  @Column(name = "idempotency_key", nullable = false, length = 260)
  private String idempotencyKey;

  @Column(name = "receipt_observed_at")
  private LocalDateTime receiptObservedAt;

  @Column(name = "receipt_status", length = 30)
  private String receiptStatus;

  @Column(name = "terminal_reason", length = 120)
  private String terminalReason;

  @Column(name = "superseded_by_attempt_id")
  private Long supersededByAttemptId;

  @Column(name = "signed_at")
  private LocalDateTime signedAt;

  @Column(name = "broadcast_started_at")
  private LocalDateTime broadcastStartedAt;

  @Column(name = "broadcasted_at")
  private LocalDateTime broadcastedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (status == null) {
      status = SponsorNonceAttemptStatus.RESERVED;
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    if (updatedAt == null) {
      updatedAt = LocalDateTime.now();
    }
  }
}
