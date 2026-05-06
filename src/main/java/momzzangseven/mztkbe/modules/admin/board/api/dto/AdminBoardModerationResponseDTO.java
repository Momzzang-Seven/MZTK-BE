package momzzangseven.mztkbe.modules.admin.board.api.dto;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;

/** Response DTO for admin board moderation APIs. */
public record AdminBoardModerationResponseDTO(
    Long targetId,
    AdminBoardModerationTargetType targetType,
    AdminBoardModerationReasonCode reasonCode,
    boolean moderated) {

  public static AdminBoardModerationResponseDTO from(AdminBoardModerationResult result) {
    return new AdminBoardModerationResponseDTO(
        result.targetId(), result.targetType(), result.reasonCode(), result.moderated());
  }
}
