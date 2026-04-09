package momzzangseven.mztkbe.global.audit.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_action_audits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminActionAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "operator_id", nullable = false)
  private Long operatorId;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

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
  private OffsetDateTime createdAt;
}
