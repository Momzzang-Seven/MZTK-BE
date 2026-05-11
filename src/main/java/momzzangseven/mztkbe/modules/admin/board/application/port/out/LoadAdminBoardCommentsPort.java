package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSortKey;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;
import org.springframework.data.domain.Page;

/** Output port for loading admin board global comment search rows. */
public interface LoadAdminBoardCommentsPort {

  Page<AdminBoardCommentView> load(AdminBoardCommentQuery query);

  record AdminBoardCommentQuery(
      String search,
      Long commentId,
      Long userId,
      AdminBoardCommentTargetType targetType,
      int page,
      int size,
      AdminBoardCommentSortKey sortKey) {}

  record AdminBoardCommentView(
      Long commentId,
      Long postId,
      Long answerId,
      Long parentId,
      AdminBoardCommentTargetType targetType,
      Long userId,
      String content,
      boolean isDeleted,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {}
}
