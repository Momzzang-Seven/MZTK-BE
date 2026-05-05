package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationExecutionMode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetFlowType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;

@Entity
@Table(name = "admin_board_moderation_actions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminBoardModerationActionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "operator_id", nullable = false)
  private Long operatorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20)
  private AdminBoardModerationTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Column(name = "post_id")
  private Long postId;

  @Enumerated(EnumType.STRING)
  @Column(name = "board_type", length = 20)
  private AdminBoardType boardType;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", nullable = false, length = 40)
  private AdminBoardModerationReasonCode reasonCode;

  @Column(name = "reason_detail", length = 500)
  private String reasonDetail;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_flow_type", nullable = false, length = 30)
  private AdminBoardModerationTargetFlowType targetFlowType;

  @Enumerated(EnumType.STRING)
  @Column(name = "execution_mode", nullable = false, length = 20)
  private AdminBoardModerationExecutionMode executionMode;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
