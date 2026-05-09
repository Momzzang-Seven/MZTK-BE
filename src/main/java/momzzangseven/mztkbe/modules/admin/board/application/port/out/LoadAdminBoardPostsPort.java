package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostSortKey;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import org.springframework.data.domain.Page;

/** Output port for loading post-owned admin board rows. */
public interface LoadAdminBoardPostsPort {

  List<AdminBoardPostView> load(AdminBoardPostQuery query);

  /**
   * Counts post-owned rows matching the same filters used by {@link #load(AdminBoardPostQuery)}.
   */
  long count(AdminBoardPostQuery query);

  Page<AdminBoardPostView> loadPage(AdminBoardPostPageQuery query);

  record AdminBoardPostQuery(
      String search,
      AdminBoardPostStatus status,
      AdminBoardPostType type,
      AdminBoardPostPublicationStatus publicationStatus,
      AdminBoardPostModerationStatus moderationStatus) {}

  record AdminBoardPostPageQuery(
      String search,
      AdminBoardPostStatus status,
      AdminBoardPostType type,
      AdminBoardPostPublicationStatus publicationStatus,
      AdminBoardPostModerationStatus moderationStatus,
      int page,
      int size,
      AdminBoardPostSortKey sortKey) {}

  record AdminBoardPostView(
      Long postId,
      AdminBoardPostType type,
      AdminBoardPostStatus status,
      AdminBoardPostPublicationStatus publicationStatus,
      AdminBoardPostModerationStatus moderationStatus,
      String title,
      String content,
      Long writerId,
      LocalDateTime createdAt) {}
}
