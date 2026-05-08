package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;

/** Result for admin board moderation requests. */
public record AdminBoardModerationResult(
    Long targetId,
    AdminBoardModerationTargetType targetType,
    AdminBoardModerationReasonCode reasonCode,
    boolean moderated,
    AdminBoardPostPublicationStatus publicationStatus,
    AdminBoardPostModerationStatus moderationStatus) {

  public AdminBoardModerationResult(
      Long targetId,
      AdminBoardModerationTargetType targetType,
      AdminBoardModerationReasonCode reasonCode,
      boolean moderated) {
    this(targetId, targetType, reasonCode, moderated, null, null);
  }
}
