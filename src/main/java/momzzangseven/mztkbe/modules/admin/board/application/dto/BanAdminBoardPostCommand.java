package momzzangseven.mztkbe.modules.admin.board.application.dto;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;

/** Command for admin post ban requests. */
public record BanAdminBoardPostCommand(
    Long operatorUserId,
    Long postId,
    AdminBoardModerationReasonCode reasonCode,
    String reasonDetail) {

  public void validate() {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
    }
    if (postId == null || postId <= 0) {
      throw new IllegalArgumentException("postId must be positive");
    }
    if (reasonCode == null) {
      throw new IllegalArgumentException("reasonCode is required");
    }
    if (reasonDetail != null && reasonDetail.length() > 500) {
      throw new IllegalArgumentException("reasonDetail must be 500 characters or fewer");
    }
  }
}
