package momzzangseven.mztkbe.modules.admin.board.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.BanAdminBoardPostUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.ChangeAdminBoardPostModerationPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.SaveAdminBoardModerationActionPort;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationExecutionMode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetFlowType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin post ban requests. */
@Service
@RequiredArgsConstructor
public class BanAdminBoardPostService implements BanAdminBoardPostUseCase {

  private final ChangeAdminBoardPostModerationPort changeAdminBoardPostModerationPort;
  private final LoadAdminBoardPostModerationTargetPort loadAdminBoardPostModerationTargetPort;
  private final SaveAdminBoardModerationActionPort saveAdminBoardModerationActionPort;
  private final Clock appClock;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "ADMIN_BOARD_POST_BAN",
      targetType = AuditTargetType.POST,
      operatorId = "#command.operatorUserId",
      targetId = "#command.postId",
      detail = {
        "moderated=#result?.moderated()",
        "publicationStatus=#result?.publicationStatus()",
        "moderationStatus=#result?.moderationStatus()"
      })
  public AdminBoardModerationResult execute(BanAdminBoardPostCommand command) {
    command.validate();
    ChangeAdminBoardPostModerationPort.AdminBoardPostModerationChangeResult result =
        changeAdminBoardPostModerationPort.block(command.operatorUserId(), command.postId());
    if (result.moderated()) {
      LoadAdminBoardPostModerationTargetPort.AdminBoardPostModerationTarget postTarget =
          loadAdminBoardPostModerationTargetPort.load(result.postId());
      saveAdminBoardModerationActionPort.save(
          AdminBoardModerationAction.create(
              command.operatorUserId(),
              AdminBoardModerationTargetType.POST,
              result.postId(),
              result.postId(),
              postTarget.boardType(),
              command.reasonCode(),
              command.reasonDetail(),
              AdminBoardModerationTargetFlowType.STANDARD,
              AdminBoardModerationExecutionMode.UNKNOWN,
              LocalDateTime.now(appClock)));
    }
    return new AdminBoardModerationResult(
        result.postId(),
        AdminBoardModerationTargetType.POST,
        command.reasonCode(),
        result.moderated(),
        result.publicationStatus(),
        result.moderationStatus());
  }
}
