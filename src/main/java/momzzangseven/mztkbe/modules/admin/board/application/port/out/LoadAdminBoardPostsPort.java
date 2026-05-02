package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** Output port for loading post-owned admin board rows. */
public interface LoadAdminBoardPostsPort {

  List<AdminBoardPostView> load(AdminBoardPostQuery query);

  record AdminBoardPostQuery(String search, PostStatus status) {}

  record AdminBoardPostView(
      Long postId,
      PostType type,
      PostStatus status,
      String title,
      String content,
      Long writerId,
      LocalDateTime createdAt) {}
}
