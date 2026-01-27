package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEventType;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;

/** WalletEvent JPA Entity */
@Entity
@Table(
    name = "wallet_events",
    indexes = {
      @Index(name = "idx_wallet_events_address", columnList = "wallet_address"),
      @Index(name = "idx_wallet_events_user_id", columnList = "user_id"),
      @Index(name = "idx_wallet_events_occurred_at", columnList = "occurred_at"),
      @Index(name = "idx_wallet_events_event_type", columnList = "event_type")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WalletEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "wallet_address", nullable = false, length = 42)
  private String walletAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 20)
  private WalletEventType eventType;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "previous_user_id")
  private Long previousUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", length = 20)
  private WalletStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", length = 20)
  private WalletStatus newStatus;

  @Column(name = "metadata", columnDefinition = "text")
  private String metadata;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
