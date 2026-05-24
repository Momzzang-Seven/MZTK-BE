package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;

@Entity
@Table(
    name = "web3_nonce_slots",
    indexes = {
      @Index(
          name = "idx_web3_nonce_slots_scope_status_nonce",
          columnList = "chain_id,from_address,status,nonce"),
      @Index(
          name = "idx_web3_nonce_slots_stale_broadcast",
          columnList =
              "chain_id,from_address,status,broadcast_started_at,broadcast_recovery_claim_expires_at"),
      @Index(name = "idx_web3_nonce_slots_active_tx", columnList = "active_tx_id"),
      @Index(name = "idx_web3_nonce_slots_active_hash", columnList = "active_tx_hash")
    })
@IdClass(NonceSlotId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NonceSlotEntity {

  @Id
  @Column(name = "chain_id", nullable = false)
  private Long chainId;

  @Id
  @Column(name = "from_address", nullable = false, length = 42)
  private String fromAddress;

  @Id
  @Column(name = "nonce", nullable = false)
  private Long nonce;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private SponsorNonceSlotStatus status;

  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo;

  @Column(name = "active_attempt_id")
  private Long activeAttemptId;

  @Column(name = "active_tx_id")
  private Long activeTxId;

  @Column(name = "active_tx_hash", length = 66)
  private String activeTxHash;

  @Column(name = "consumed_attempt_id")
  private Long consumedAttemptId;

  @Column(name = "consumed_tx_id")
  private Long consumedTxId;

  @Column(name = "consumed_external_evidence_id")
  private Long consumedExternalEvidenceId;

  @Column(name = "consumed_at")
  private LocalDateTime consumedAt;

  @Column(name = "consumed_reason", length = 120)
  private String consumedReason;

  @Column(name = "released_attempt_id")
  private Long releasedAttemptId;

  @Column(name = "released_tx_id")
  private Long releasedTxId;

  @Column(name = "released_at")
  private LocalDateTime releasedAt;

  @Column(name = "release_reason", length = 120)
  private String releaseReason;

  @Column(name = "stuck_reason", length = 120)
  private String stuckReason;

  @Column(name = "replacement_claim_owner", length = 120)
  private String replacementClaimOwner;

  @Column(name = "replacement_claim_expires_at")
  private LocalDateTime replacementClaimExpiresAt;

  @Column(name = "replacement_prepare_attempt_count", nullable = false)
  private Integer replacementPrepareAttemptCount;

  @Column(name = "broadcast_started_at")
  private LocalDateTime broadcastStartedAt;

  @Column(name = "last_broadcasted_at")
  private LocalDateTime lastBroadcastedAt;

  @Column(name = "broadcast_recovery_claim_owner", length = 120)
  private String broadcastRecoveryClaimOwner;

  @Column(name = "broadcast_recovery_claim_token", length = 120)
  private String broadcastRecoveryClaimToken;

  @Column(name = "broadcast_recovery_claim_expires_at")
  private LocalDateTime broadcastRecoveryClaimExpiresAt;

  @Column(name = "broadcast_recovery_attempt_count", nullable = false)
  private Integer broadcastRecoveryAttemptCount;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (status == null) {
      status = SponsorNonceSlotStatus.RESERVED;
    }
    if (attemptNo == null) {
      attemptNo = 0;
    }
    if (replacementPrepareAttemptCount == null) {
      replacementPrepareAttemptCount = 0;
    }
    if (broadcastRecoveryAttemptCount == null) {
      broadcastRecoveryAttemptCount = 0;
    }
    if (version == null) {
      version = 0L;
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
    updatedAt = LocalDateTime.now();
  }
}
