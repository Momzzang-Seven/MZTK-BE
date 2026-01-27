package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "user_wallets",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_user_wallets_address", columnNames = "wallet_address")
    },
    indexes = {
      @Index(name = "idx_user_wallets_status", columnList = "status"),
      @Index(name = "idx_user_wallets_user_id", columnList = "user_id")
    })
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserWalletEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "wallet_address", length = 42, nullable = false)
  private String walletAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private WalletStatus status;

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  @Column(name = "unlinked_at")
  private Instant unlinkedAt;

  @Column(name = "user_deleted_at")
  private Instant userDeletedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (this.registeredAt == null) {
      this.registeredAt = Instant.now();
    }
    if (this.status == null) {
      this.status = WalletStatus.ACTIVE;
    }
  }
}
