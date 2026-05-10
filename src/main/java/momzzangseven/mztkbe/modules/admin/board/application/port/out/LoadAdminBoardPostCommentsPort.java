package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import org.springframework.data.domain.Page;

/** Output port for loading comment-owned admin board rows. */
public interface LoadAdminBoardPostCommentsPort {

  Page<AdminBoardCommentView> load(GetAdminBoardPostCommentsCommand command);

  record AdminBoardCommentView(
      Long commentId,
      Long postId,
      Long writerId,
      String content,
      Long parentId,
      boolean isDeleted,
      LocalDateTime createdAt) {}
}
