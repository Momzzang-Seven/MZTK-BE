package momzzangseven.mztkbe.modules.admin.board.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.UnblockAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.UnblockAdminBoardPostUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.ChangeAdminBoardPostModerationPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin post unblock requests. */
@Service
@RequiredArgsConstructor
public class UnblockAdminBoardPostService implements UnblockAdminBoardPostUseCase {

  private final ChangeAdminBoardPostModerationPort changeAdminBoardPostModerationPort;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "ADMIN_BOARD_POST_UNBLOCK",
      targetType = AuditTargetType.POST,
      operatorId = "#command.operatorUserId",
      targetId = "#command.postId",
      detail = {
        "moderated=#result?.moderated()",
        "publicationStatus=#result?.publicationStatus()",
        "moderationStatus=#result?.moderationStatus()"
      })
  public AdminBoardModerationResult execute(UnblockAdminBoardPostCommand command) {
    command.validate();
    ChangeAdminBoardPostModerationPort.AdminBoardPostModerationChangeResult result =
        changeAdminBoardPostModerationPort.unblock(command.operatorUserId(), command.postId());
    return new AdminBoardModerationResult(
        result.postId(),
        AdminBoardModerationTargetType.POST,
        command.reasonCode(),
        result.moderated(),
        result.publicationStatus(),
        result.moderationStatus());
  }
}
