package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;

/** Command for admin comment ban requests. */
public record BanAdminBoardCommentCommand(
    Long operatorUserId,
    Long commentId,
    AdminBoardModerationReasonCode reasonCode,
    String reasonDetail) {

  public void validate() {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
    }
    if (commentId == null || commentId <= 0) {
      throw new IllegalArgumentException("commentId must be positive");
    }
    if (reasonCode == null) {
      throw new IllegalArgumentException("reasonCode is required");
    }
    if (reasonDetail != null && reasonDetail.length() > 500) {
      throw new IllegalArgumentException("reasonDetail must be 500 characters or fewer");
    }
  }
}
