package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;

@Entity
@Table(name = "web3_transaction_audits")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3TransactionAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "web3_transaction_id", nullable = false)
  private Long web3TransactionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 30)
  private Web3TransactionAuditEventType eventType;

  @Column(name = "rpc_alias", length = 10)
  private String rpcAlias;

  @Column(name = "detail_json", columnDefinition = "TEXT")
  private String detailJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
