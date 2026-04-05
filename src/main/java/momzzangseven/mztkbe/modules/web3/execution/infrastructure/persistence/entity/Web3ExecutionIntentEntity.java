package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.entity;

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
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;

@Entity
@Table(
    name = "web3_execution_intents",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_web3_execution_intents_public_id", columnNames = "public_id"),
      @UniqueConstraint(
          name = "uk_web3_execution_intents_root_attempt",
          columnNames = {"root_idempotency_key", "attempt_no"}),
      @UniqueConstraint(
          name = "uk_web3_execution_intents_submitted_tx",
          columnNames = "submitted_tx_id")
    },
    indexes = {
      @Index(
          name = "idx_web3_execution_intents_resource",
          columnList = "resource_type,resource_id,created_at"),
      @Index(
          name = "idx_web3_execution_intents_root_status",
          columnList = "root_idempotency_key,status,created_at"),
      @Index(
          name = "idx_web3_execution_intents_cleanup",
          columnList = "status,expires_at,updated_at"),
      @Index(
          name = "idx_web3_execution_intents_requester_status",
          columnList = "requester_user_id,status,created_at")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3ExecutionIntentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, length = 36)
  private String publicId;

  @Column(name = "root_idempotency_key", nullable = false, length = 250)
  private String rootIdempotencyKey;

  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 40)
  private ExecutionResourceType resourceType;

  @Column(name = "resource_id", nullable = false, length = 250)
  private String resourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 60)
  private ExecutionActionType actionType;

  @Column(name = "requester_user_id", nullable = false)
  private Long requesterUserId;

  @Column(name = "counterparty_user_id")
  private Long counterpartyUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode", nullable = false, length = 20)
  private ExecutionMode mode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ExecutionIntentStatus status;

  @Column(name = "payload_hash", nullable = false, length = 66)
  private String payloadHash;

  @Column(name = "payload_snapshot_json", columnDefinition = "TEXT")
  private String payloadSnapshotJson;

  @Column(name = "authority_address", length = 42)
  private String authorityAddress;

  @Column(name = "authority_nonce")
  private Long authorityNonce;

  @Column(name = "delegate_target", length = 42)
  private String delegateTarget;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "authorization_payload_hash", length = 66)
  private String authorizationPayloadHash;

  @Column(name = "execution_digest", length = 66)
  private String executionDigest;

  @Column(name = "unsigned_tx_snapshot", columnDefinition = "TEXT")
  private String unsignedTxSnapshot;

  @Column(name = "unsigned_tx_fingerprint", length = 66)
  private String unsignedTxFingerprint;

  @Column(name = "reserved_sponsor_cost_wei", nullable = false, precision = 78, scale = 0)
  private BigInteger reservedSponsorCostWei;

  @Column(name = "sponsor_usage_date_kst")
  private LocalDate sponsorUsageDateKst;

  @Column(name = "submitted_tx_id")
  private Long submittedTxId;

  @Column(name = "last_error_code", length = 120)
  private String lastErrorCode;

  @Column(name = "last_error_reason", columnDefinition = "TEXT")
  private String lastErrorReason;

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
    if (status == null) {
      status = ExecutionIntentStatus.AWAITING_SIGNATURE;
    }
    if (reservedSponsorCostWei == null) {
      reservedSponsorCostWei = BigInteger.ZERO;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
