package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

public interface LoadAdminBoardPostModerationTargetPort {

  AdminBoardPostModerationTarget load(Long postId);

  record AdminBoardPostModerationTarget(Long postId, AdminBoardType boardType, PostStatus status) {}
}
