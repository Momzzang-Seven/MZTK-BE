package momzzangseven.mztkbe.modules.admin.board.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardCommentCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.BanAdminBoardCommentUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.BanAdminBoardCommentPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.SaveAdminBoardModerationActionPort;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationExecutionMode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetFlowType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin comment ban requests. */
@Service
@RequiredArgsConstructor
public class BanAdminBoardCommentService implements BanAdminBoardCommentUseCase {

  private final BanAdminBoardCommentPort banAdminBoardCommentPort;
  private final LoadAdminBoardPostModerationTargetPort loadAdminBoardPostModerationTargetPort;
  private final SaveAdminBoardModerationActionPort saveAdminBoardModerationActionPort;
  private final Clock appClock;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "ADMIN_BOARD_COMMENT_BAN",
      targetType = AuditTargetType.COMMENT,
      operatorId = "#command.operatorUserId",
      targetId = "#command.commentId")
  public AdminBoardModerationResult execute(BanAdminBoardCommentCommand command) {
    command.validate();

    BanAdminBoardCommentPort.BanAdminBoardCommentResult banned =
        banAdminBoardCommentPort.ban(command.commentId());
    if (banned.moderated()) {
      LoadAdminBoardPostModerationTargetPort.AdminBoardPostModerationTarget postTarget =
          loadAdminBoardPostModerationTargetPort.load(banned.postId());
      saveAdminBoardModerationActionPort.save(
          AdminBoardModerationAction.create(
              command.operatorUserId(),
              AdminBoardModerationTargetType.COMMENT,
              banned.commentId(),
              banned.postId(),
              postTarget.boardType(),
              command.reasonCode(),
              command.reasonDetail(),
              AdminBoardModerationTargetFlowType.STANDARD,
              AdminBoardModerationExecutionMode.SOFT_DELETE,
              LocalDateTime.now(appClock)));
    }

    return new AdminBoardModerationResult(
        banned.commentId(),
        AdminBoardModerationTargetType.COMMENT,
        command.reasonCode(),
        banned.moderated());
  }
}
