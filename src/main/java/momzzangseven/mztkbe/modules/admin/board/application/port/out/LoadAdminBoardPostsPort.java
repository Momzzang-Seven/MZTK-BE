package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostSortKey;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import org.springframework.data.domain.Page;

/** Output port for loading post-owned admin board rows. */
public interface LoadAdminBoardPostsPort {

  List<AdminBoardPostView> load(AdminBoardPostQuery query);

  Page<AdminBoardPostView> loadPage(AdminBoardPostPageQuery query);

  record AdminBoardPostQuery(String search, AdminBoardPostStatus status) {}

  record AdminBoardPostPageQuery(
      String search,
      AdminBoardPostStatus status,
      int page,
      int size,
      AdminBoardPostSortKey sortKey) {}

  record AdminBoardPostView(
      Long postId,
      AdminBoardPostType type,
      AdminBoardPostStatus status,
      String title,
      String content,
      Long writerId,
      LocalDateTime createdAt) {}
}
