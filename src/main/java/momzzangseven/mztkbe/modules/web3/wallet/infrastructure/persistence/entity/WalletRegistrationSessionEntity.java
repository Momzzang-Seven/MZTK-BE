package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;

@Entity
@Table(
    name = "web3_wallet_registration_sessions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_web3_wallet_registration_sessions_public_id",
          columnNames = "public_id"),
      @UniqueConstraint(
          name = "uk_web3_wallet_registration_sessions_challenge_nonce",
          columnNames = "challenge_nonce"),
      @UniqueConstraint(
          name = "uk_web3_wallet_registration_sessions_latest_intent",
          columnNames = "latest_execution_intent_id")
    },
    indexes = {
      @Index(
          name = "idx_web3_wallet_registration_sessions_user_status",
          columnList = "user_id,status,created_at"),
      @Index(
          name = "idx_web3_wallet_registration_sessions_wallet_status",
          columnList = "wallet_address,status,created_at"),
      @Index(
          name = "idx_web3_wallet_registration_sessions_latest_tx",
          columnList = "latest_transaction_id"),
      @Index(
          name = "idx_web3_wallet_registration_sessions_user_created_id",
          columnList = "user_id,created_at,id"),
      @Index(
          name = "idx_web3_wallet_registration_sessions_wallet_created_id",
          columnList = "wallet_address,created_at,id"),
      @Index(
          name = "idx_web3_wallet_registration_sessions_user_id_desc",
          columnList = "user_id,id"),
      @Index(
          name = "idx_web3_wallet_registration_sessions_wallet_id_desc",
          columnList = "wallet_address,id"),
      @Index(
          name = "idx_web3_wallet_registration_sessions_status_updated_id",
          columnList = "status,updated_at,id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WalletRegistrationSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, length = 36)
  private String publicId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "wallet_address", nullable = false, length = 42)
  private String walletAddress;

  @Column(name = "challenge_nonce", nullable = false, length = 100)
  private String challengeNonce;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private WalletRegistrationStatus status;

  @Column(name = "latest_execution_intent_id", length = 100)
  private String latestExecutionIntentId;

  @Column(name = "receipt_timeout_execution_intent_ids", columnDefinition = "TEXT")
  private String receiptTimeoutExecutionIntentIds;

  @Column(name = "latest_transaction_id")
  private Long latestTransactionId;

  @Column(name = "latest_transaction_hash", length = 66)
  private String latestTransactionHash;

  @Column(name = "last_execution_status", length = 40)
  private String lastExecutionStatus;

  @Column(name = "last_error_code", length = 120)
  private String lastErrorCode;

  @Column(name = "last_error_reason", columnDefinition = "TEXT")
  private String lastErrorReason;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount;

  @Column(name = "approval_expires_at")
  private LocalDateTime approvalExpiresAt;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @Column(name = "finalized_at")
  private LocalDateTime finalizedAt;

  @Column(name = "registered_wallet_id")
  private Long registeredWalletId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (status == null) {
      status = WalletRegistrationStatus.APPROVAL_REQUIRED;
    }
    if (retryCount == null) {
      retryCount = 0;
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }
}
