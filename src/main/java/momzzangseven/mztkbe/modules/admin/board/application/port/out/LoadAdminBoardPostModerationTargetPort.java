package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;

public interface LoadAdminBoardPostModerationTargetPort {

  AdminBoardPostModerationTarget load(Long postId);

  record AdminBoardPostModerationTarget(
      Long postId, AdminBoardType boardType, AdminBoardPostStatus status) {}
}
