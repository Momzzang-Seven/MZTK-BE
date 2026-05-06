package momzzangseven.mztkbe.modules.admin.board.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardCommentCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;

/** Request DTO for admin board ban APIs. */
public record AdminBoardBanRequestDTO(
    @NotNull(message = "Reason code is required") AdminBoardModerationReasonCode reasonCode,
    @Size(max = 500, message = "Reason detail must not exceed 500 characters")
        String reasonDetail) {

  public BanAdminBoardPostCommand toPostCommand(Long operatorUserId, Long postId) {
    BanAdminBoardPostCommand command =
        new BanAdminBoardPostCommand(
            operatorUserId, postId, reasonCode, normalizeReasonDetail(reasonDetail));
    command.validate();
    return command;
  }

  public BanAdminBoardCommentCommand toCommentCommand(Long operatorUserId, Long commentId) {
    BanAdminBoardCommentCommand command =
        new BanAdminBoardCommentCommand(
            operatorUserId, commentId, reasonCode, normalizeReasonDetail(reasonDetail));
    command.validate();
    return command;
  }

  private static String normalizeReasonDetail(String reasonDetail) {
    if (reasonDetail == null || reasonDetail.trim().isBlank()) {
      return null;
    }
    return reasonDetail.trim();
  }
}
