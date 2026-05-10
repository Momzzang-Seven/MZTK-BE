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
    AdminBoardPostModerationStatus moderationStatus,
    Boolean publiclyVisible) {

  /** Creates a post moderation result with public visibility derived from post state values. */
  public AdminBoardModerationResult(
      Long targetId,
      AdminBoardModerationTargetType targetType,
      AdminBoardModerationReasonCode reasonCode,
      boolean moderated,
      AdminBoardPostPublicationStatus publicationStatus,
      AdminBoardPostModerationStatus moderationStatus) {
    this(
        targetId,
        targetType,
        reasonCode,
        moderated,
        publicationStatus,
        moderationStatus,
        isPubliclyVisible(publicationStatus, moderationStatus));
  }

  /** Creates a moderation result for targets that do not carry post state values. */
  public AdminBoardModerationResult(
      Long targetId,
      AdminBoardModerationTargetType targetType,
      AdminBoardModerationReasonCode reasonCode,
      boolean moderated) {
    this(targetId, targetType, reasonCode, moderated, null, null, null);
  }

  private static Boolean isPubliclyVisible(
      AdminBoardPostPublicationStatus publicationStatus,
      AdminBoardPostModerationStatus moderationStatus) {
    if (publicationStatus == null || moderationStatus == null) {
      return null;
    }
    return publicationStatus == AdminBoardPostPublicationStatus.VISIBLE
        && moderationStatus == AdminBoardPostModerationStatus.NORMAL;
  }
}
