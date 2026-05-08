package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;

public interface ChangeAdminBoardPostModerationPort {

  AdminBoardPostModerationChangeResult block(Long operatorUserId, Long postId);

  AdminBoardPostModerationChangeResult unblock(Long operatorUserId, Long postId);

  record AdminBoardPostModerationChangeResult(
      Long postId,
      boolean moderated,
      AdminBoardPostPublicationStatus publicationStatus,
      AdminBoardPostModerationStatus moderationStatus) {}
}
