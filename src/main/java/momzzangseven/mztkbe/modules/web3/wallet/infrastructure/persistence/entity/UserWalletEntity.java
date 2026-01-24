package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;

@Entity
@Table(
    name = "user_wallets",
        indexes = {
        @Index(
                name = "unique_active_wallet_address",
                columnList = "wallet_address",
                unique = false)
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
