package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;

@Entity
@Table(
    name = "web3_transactions",
    uniqueConstraints = {@UniqueConstraint(name = "uk_web3_tx_idempotency", columnNames = "idempotency_key")},
    indexes = {
      @Index(name = "idx_web3_tx_status", columnList = "status"),
      @Index(name = "idx_web3_tx_processing_until", columnList = "processing_until"),
      @Index(name = "idx_web3_tx_reference", columnList = "reference_type,reference_id")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3TransactionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_type", nullable = false, length = 30)
  private Web3ReferenceType referenceType;

  @Column(name = "reference_id", nullable = false, length = 100)
  private String referenceId;

  @Column(name = "from_user_id")
  private Long fromUserId;

  @Column(name = "to_user_id")
  private Long toUserId;

  @Column(name = "from_address", nullable = false, length = 42)
  private String fromAddress;

  @Column(name = "to_address", nullable = false, length = 42)
  private String toAddress;

  @Column(name = "amount_wei", nullable = false, precision = 78, scale = 0)
  private BigInteger amountWei;

  @Column(name = "nonce")
  private Long nonce;

  @Enumerated(EnumType.STRING)
  @Column(name = "tx_type", nullable = false, length = 20)
  private Web3TxType txType;

  @Column(name = "authority_address", length = 42)
  private String authorityAddress;

  @Column(name = "authorization_nonce")
  private Long authorizationNonce;

  @Column(name = "delegate_target", length = 42)
  private String delegateTarget;

  @Column(name = "authorization_expires_at")
  private LocalDateTime authorizationExpiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Web3TxStatus status;

  @Column(name = "tx_hash", length = 66)
  private String txHash;

  @Column(name = "signed_at")
  private LocalDateTime signedAt;

  @Column(name = "broadcasted_at")
  private LocalDateTime broadcastedAt;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @Column(name = "signed_raw_tx", columnDefinition = "TEXT")
  private String signedRawTx;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @Column(name = "processing_until")
  private LocalDateTime processingUntil;

  @Column(name = "processing_by", length = 64)
  private String processingBy;

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
      status = Web3TxStatus.CREATED;
    }
    if (txType == null) {
      txType = Web3TxType.EIP1559;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
