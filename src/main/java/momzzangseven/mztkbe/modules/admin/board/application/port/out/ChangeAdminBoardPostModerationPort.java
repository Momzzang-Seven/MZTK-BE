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
      AdminBoardPostModerationStatus moderationStatus,
      Boolean publiclyVisible) {

    /** Creates a result while deriving public visibility from the returned post states. */
    public AdminBoardPostModerationChangeResult(
        Long postId,
        boolean moderated,
        AdminBoardPostPublicationStatus publicationStatus,
        AdminBoardPostModerationStatus moderationStatus) {
      this(
          postId,
          moderated,
          publicationStatus,
          moderationStatus,
          isPubliclyVisible(publicationStatus, moderationStatus));
    }

    private static Boolean isPubliclyVisible(
        AdminBoardPostPublicationStatus publicationStatus,
        AdminBoardPostModerationStatus moderationStatus) {
      if (publicationStatus == null || moderationStatus == null) {
        return null;
      }
      return publicationStatus == AdminBoardPostPublicationStatus.VISIBLE
          && moderationStatus == AdminBoardPostModerationStatus.NORMAL;
    }
  }
}
