package momzzangseven.mztkbe.modules.admin.board.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.BanAdminBoardPostUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin post ban requests. */
@Service
@RequiredArgsConstructor
public class BanAdminBoardPostService implements BanAdminBoardPostUseCase {

  private final LoadAdminBoardPostModerationTargetPort loadAdminBoardPostModerationTargetPort;

  @Override
  @Transactional(readOnly = true)
  @AdminOnly(
      actionType = "ADMIN_BOARD_POST_BAN",
      targetType = AuditTargetType.POST,
      operatorId = "#command.operatorUserId",
      targetId = "#command.postId")
  public AdminBoardModerationResult execute(BanAdminBoardPostCommand command) {
    command.validate();
    LoadAdminBoardPostModerationTargetPort.AdminBoardPostModerationTarget postTarget =
        loadAdminBoardPostModerationTargetPort.load(command.postId());

    throw new BusinessException(
        ErrorCode.ADMIN_BOARD_POST_BAN_POLICY_UNCONFIRMED,
        "Admin post ban policy is not confirmed for boardType=" + postTarget.boardType());
  }
}
