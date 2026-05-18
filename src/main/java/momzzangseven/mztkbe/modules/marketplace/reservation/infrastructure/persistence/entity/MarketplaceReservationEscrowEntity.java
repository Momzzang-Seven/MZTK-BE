package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "marketplace_reservation_escrows",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_marketplace_reservation_escrows_reservation_id",
          columnNames = "reservation_id"),
      @UniqueConstraint(
          name = "uk_marketplace_reservation_escrows_order_key",
          columnNames = "order_key"),
      @UniqueConstraint(
          name = "uk_marketplace_reservation_escrows_id_reservation",
          columnNames = {"id", "reservation_id"})
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketplaceReservationEscrowEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reservation_id", nullable = false)
  private Long reservationId;

  @Column(name = "escrow_flow", nullable = false, length = 30)
  private String escrowFlow;

  @Column(name = "escrow_status", nullable = false, length = 40)
  private String escrowStatus;

  @Column(name = "order_key", length = 66)
  private String orderKey;

  @Column(name = "buyer_wallet_address", length = 42)
  private String buyerWalletAddress;

  @Column(name = "trainer_wallet_address", length = 42)
  private String trainerWalletAddress;

  @Column(name = "token_address", length = 42)
  private String tokenAddress;

  @Column(name = "price_base_units", precision = 78, scale = 0)
  private BigDecimal priceBaseUnits;

  @Column(name = "hold_expires_at")
  private LocalDateTime holdExpiresAt;

  @Column(name = "expected_contract_deadline_epoch_seconds")
  private Long expectedContractDeadlineEpochSeconds;

  @Column(name = "expected_contract_deadline_at")
  private LocalDateTime expectedContractDeadlineAt;

  @Column(name = "contract_deadline_epoch_seconds")
  private Long contractDeadlineEpochSeconds;

  @Column(name = "contract_deadline_at")
  private LocalDateTime contractDeadlineAt;

  @Column(name = "last_chain_state")
  private Integer lastChainState;

  @Column(name = "last_chain_synced_at")
  private LocalDateTime lastChainSyncedAt;

  @Column(name = "last_tx_hash", length = 66)
  private String lastTxHash;

  @Column(name = "last_failure_code", length = 120)
  private String lastFailureCode;

  @Column(name = "last_failure_message", length = 500)
  private String lastFailureMessage;

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
