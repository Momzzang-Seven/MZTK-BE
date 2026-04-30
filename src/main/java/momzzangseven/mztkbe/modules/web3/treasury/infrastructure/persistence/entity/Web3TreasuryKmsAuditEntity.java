package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "web3_treasury_kms_audits")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3TreasuryKmsAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "operator_id")
  private Long operatorId;

  @Column(name = "wallet_alias", nullable = false, length = 64)
  private String walletAlias;

  @Column(name = "kms_key_id", length = 128)
  private String kmsKeyId;

  @Column(name = "wallet_address", length = 42)
  private String walletAddress;

  @Column(name = "action_type", nullable = false, length = 32)
  private String actionType;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "failure_reason", length = 256)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
