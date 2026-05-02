package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;

/** Result for admin board moderation requests. */
public record AdminBoardModerationResult(
    Long targetId,
    AdminBoardModerationTargetType targetType,
    AdminBoardModerationReasonCode reasonCode,
    boolean moderated) {}
