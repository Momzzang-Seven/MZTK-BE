package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "web3_sponsor_daily_usage",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_web3_sponsor_daily_usage",
          columnNames = {"user_id", "usage_date_kst"})
    },
    indexes = {@Index(name = "idx_web3_sponsor_daily_usage_date", columnList = "usage_date_kst")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3SponsorDailyUsageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "usage_date_kst", nullable = false)
  private LocalDate usageDateKst;

  @Column(name = "reserved_cost_wei", nullable = false, precision = 78, scale = 0)
  private BigInteger reservedCostWei;

  @Column(name = "consumed_cost_wei", nullable = false, precision = 78, scale = 0)
  private BigInteger consumedCostWei;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    if (reservedCostWei == null) {
      reservedCostWei = BigInteger.ZERO;
    }
    if (consumedCostWei == null) {
      consumedCostWei = BigInteger.ZERO;
    }
  }
}
