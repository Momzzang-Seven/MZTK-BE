package momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.entity;

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
@Table(name = "web3_admin_action_audits")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Web3AdminActionAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "operator_id", nullable = false)
  private Long operatorId;

  @Column(name = "action_type", nullable = false, length = 60)
  private String actionType;

  @Column(name = "target_type", nullable = false, length = 40)
  private String targetType;

  @Column(name = "target_id", length = 100)
  private String targetId;

  @Column(name = "success", nullable = false)
  private boolean success;

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
