package momzzangseven.mztkbe.modules.admin.board.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationExecutionMode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetFlowType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;

@Getter
@Builder(toBuilder = true)
public class AdminBoardModerationAction {
  private final Long id;
  private final Long operatorId;
  private final AdminBoardModerationTargetType targetType;
  private final Long targetId;
  private final Long postId;
  private final AdminBoardType boardType;
  private final AdminBoardModerationReasonCode reasonCode;
  private final String reasonDetail;
  private final AdminBoardModerationTargetFlowType targetFlowType;
  private final AdminBoardModerationExecutionMode executionMode;
  private final LocalDateTime createdAt;

  public static AdminBoardModerationAction create(
      Long operatorId,
      AdminBoardModerationTargetType targetType,
      Long targetId,
      Long postId,
      AdminBoardType boardType,
      AdminBoardModerationReasonCode reasonCode,
      String reasonDetail,
      AdminBoardModerationTargetFlowType targetFlowType,
      AdminBoardModerationExecutionMode executionMode) {
    if (operatorId == null || operatorId <= 0) {
      throw new IllegalArgumentException("operatorId must be positive");
    }
    if (targetType == null) {
      throw new IllegalArgumentException("targetType is required");
    }
    if (targetId == null || targetId <= 0) {
      throw new IllegalArgumentException("targetId must be positive");
    }
    if (reasonCode == null) {
      throw new IllegalArgumentException("reasonCode is required");
    }
    if (targetFlowType == null) {
      throw new IllegalArgumentException("targetFlowType is required");
    }
    if (executionMode == null) {
      throw new IllegalArgumentException("executionMode is required");
    }
    if (reasonDetail != null && reasonDetail.length() > 500) {
      throw new IllegalArgumentException("reasonDetail must be 500 characters or fewer");
    }
    return AdminBoardModerationAction.builder()
        .operatorId(operatorId)
        .targetType(targetType)
        .targetId(targetId)
        .postId(postId)
        .boardType(boardType)
        .reasonCode(reasonCode)
        .reasonDetail(reasonDetail)
        .targetFlowType(targetFlowType)
        .executionMode(executionMode)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
