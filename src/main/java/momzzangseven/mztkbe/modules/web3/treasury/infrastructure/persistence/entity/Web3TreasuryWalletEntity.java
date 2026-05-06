package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * JPA mapping for {@code web3_treasury_wallets}. Mirrors the {@code TreasuryWallet} aggregate.
 * After V065 every row is KMS-backed: {@code wallet_alias}, {@code treasury_address}, {@code
 * kms_key_id}, {@code status}, {@code key_origin} are all NOT NULL and {@code kms_key_id} is
 * UNIQUE.
 *
 * <p>The {@code status} and {@code keyOrigin} columns are persisted as plain strings rather than
 * {@code @Enumerated} domain enums; ARCHITECTURE.md requires the entity to stay free of domain
 * imports, so the persistence adapter is responsible for round-tripping the column strings against
 * the {@code TreasuryWalletStatus} / {@code TreasuryKeyOrigin} value objects.
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

  @Column(name = "treasury_address", nullable = false, length = 42)
  private String treasuryAddress;

  @Column(name = "kms_key_id", nullable = false, unique = true, length = 255)
  private String kmsKeyId;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "key_origin", nullable = false, length = 32)
  private String keyOrigin;

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
