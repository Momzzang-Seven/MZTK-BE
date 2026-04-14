package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferGuardAuditReason;

@Entity
@Table(
    name = "web3_transfer_guard_audits",
    indexes = {
      @Index(name = "idx_web3_transfer_guard_audits_created_at", columnList = "created_at"),
      @Index(name = "idx_web3_transfer_guard_audits_user_id", columnList = "user_id"),
      @Index(
          name = "idx_web3_transfer_guard_audits_domain_reference",
          columnList = "domain_type,reference_id")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3TransferGuardAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "client_ip", nullable = false, length = 64)
  private String clientIp;

  @Enumerated(EnumType.STRING)
  @Column(name = "domain_type", nullable = false, length = 40)
  private DomainReferenceType domainType;

  @Column(name = "reference_id", nullable = false, length = 100)
  private String referenceId;

  @Column(name = "prepare_id", length = 36)
  private String prepareId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, length = 40)
  private TransferGuardAuditReason reason;

  @Column(name = "requested_to_user_id")
  private Long requestedToUserId;

  @Column(name = "resolved_to_user_id")
  private Long resolvedToUserId;

  @Column(name = "requested_amount_wei", nullable = false, precision = 78, scale = 0)
  private BigInteger requestedAmountWei;

  @Column(name = "resolved_amount_wei", precision = 78, scale = 0)
  private BigInteger resolvedAmountWei;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
