package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;

@Entity
@Table(
    name = "web3_transfer_prepares",
    indexes = {
      @Index(
          name = "idx_web3_transfer_prepare_reference",
          columnList = "reference_type,reference_id"),
      @Index(
          name = "idx_web3_transfer_prepare_idem_created",
          columnList = "idempotency_key,created_at"),
      @Index(name = "idx_web3_transfer_prepare_expires_at", columnList = "auth_expires_at")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3TransferPrepareEntity {

  @Id
  @Column(name = "prepare_id", nullable = false, length = 36)
  private String prepareId;

  @Column(name = "from_user_id", nullable = false)
  private Long fromUserId;

  @Column(name = "to_user_id")
  private Long toUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_type", nullable = false, length = 30)
  private Web3ReferenceType referenceType;

  @Column(name = "reference_id", nullable = false, length = 100)
  private String referenceId;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "authority_address", nullable = false, length = 42)
  private String authorityAddress;

  @Column(name = "to_address", nullable = false, length = 42)
  private String toAddress;

  @Column(name = "amount_wei", nullable = false, precision = 78, scale = 0)
  private BigInteger amountWei;

  @Column(name = "authority_nonce", nullable = false)
  private Long authorityNonce;

  @Column(name = "delegate_target", nullable = false, length = 42)
  private String delegateTarget;

  @Column(name = "auth_expires_at", nullable = false)
  private LocalDateTime authExpiresAt;

  @Column(name = "payload_hash_to_sign", nullable = false, length = 66)
  private String payloadHashToSign;

  @Column(name = "salt", nullable = false, length = 66)
  private String salt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TransferPrepareStatus status;

  @Column(name = "submitted_tx_id")
  private Long submittedTxId;

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
      status = TransferPrepareStatus.CREATED;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
