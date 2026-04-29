package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;

/**
 * JPA mapping for {@code web3_treasury_wallets}. Mirrors the {@code TreasuryWallet} aggregate but
 * also retains the legacy {@code treasuryAddress} / {@code treasuryPrivateKeyEncrypted} columns
 * during the KMS migration window. The legacy private-key column is scheduled for removal in PR4
 * once all environments have switched to KMS-backed signing.
 */
@Entity
@Table(name = "web3_treasury_wallets")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3TreasuryWalletEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "wallet_alias", nullable = false, unique = true, length = 64)
  private String walletAlias;

  @Column(name = "treasury_address", length = 42)
  private String treasuryAddress;

  @Column(name = "treasury_private_key_encrypted", columnDefinition = "TEXT")
  private String treasuryPrivateKeyEncrypted;

  @Column(name = "kms_key_id", length = 255)
  private String kmsKeyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32)
  private TreasuryWalletStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "key_origin", length = 32)
  private TreasuryKeyOrigin keyOrigin;

  @Column(name = "disabled_at")
  private LocalDateTime disabledAt;

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
