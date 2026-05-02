package momzzangseven.mztkbe.modules.admin.board.application.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostSortKey;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostCommentCountsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin board post list reads. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAdminBoardPostsService implements GetAdminBoardPostsUseCase {

  private static final int CONTENT_PREVIEW_LENGTH = 120;

  private final LoadAdminBoardPostsPort loadAdminBoardPostsPort;
  private final LoadAdminBoardPostCommentCountsPort loadAdminBoardPostCommentCountsPort;
  private final LoadAdminBoardWriterNicknamesPort loadAdminBoardWriterNicknamesPort;

  @Override
  @AdminOnly(
      actionType = "ADMIN_BOARD_POSTS_VIEW",
      targetType = AuditTargetType.POST,
      operatorId = "#command.operatorUserId",
      targetId = "'posts'")
  public Page<AdminBoardPostResult> execute(GetAdminBoardPostsCommand command) {
    List<LoadAdminBoardPostsPort.AdminBoardPostView> posts =
        loadAdminBoardPostsPort.load(
            new LoadAdminBoardPostsPort.AdminBoardPostQuery(command.search(), command.status()));
    if (posts.isEmpty()) {
      return emptyPage(command.page(), command.size());
    }

    List<Long> postIds =
        posts.stream().map(LoadAdminBoardPostsPort.AdminBoardPostView::postId).toList();
    Map<Long, Long> commentCounts = loadAdminBoardPostCommentCountsPort.load(postIds);
    Map<Long, String> writerNicknames = loadAdminBoardWriterNicknamesPort.load(writerIds(posts));

    List<AdminBoardPostResult> combined =
        posts.stream()
            .map(post -> toResult(post, writerNicknames, commentCounts))
            .sorted(buildComparator(command.sortKey()))
            .toList();

    int fromIndex = Math.min(command.page() * command.size(), combined.size());
    int toIndex = Math.min(fromIndex + command.size(), combined.size());
    return new PageImpl<>(
        combined.subList(fromIndex, toIndex),
        PageRequest.of(command.page(), command.size()),
        combined.size());
  }

  private static Collection<Long> writerIds(
      List<LoadAdminBoardPostsPort.AdminBoardPostView> posts) {
    return posts.stream().map(LoadAdminBoardPostsPort.AdminBoardPostView::writerId).toList();
  }

  private AdminBoardPostResult toResult(
      LoadAdminBoardPostsPort.AdminBoardPostView post,
      Map<Long, String> writerNicknames,
      Map<Long, Long> commentCounts) {
    return new AdminBoardPostResult(
        post.postId(),
        post.type(),
        post.status(),
        post.title(),
        preview(post.content()),
        post.writerId(),
        writerNicknames.get(post.writerId()),
        post.createdAt(),
        commentCounts.getOrDefault(post.postId(), 0L));
  }

  private static String preview(String content) {
    if (content == null || content.length() <= CONTENT_PREVIEW_LENGTH) {
      return content;
    }
    return content.substring(0, CONTENT_PREVIEW_LENGTH);
  }

  private Comparator<AdminBoardPostResult> buildComparator(AdminBoardPostSortKey sortKey) {
    Comparator<AdminBoardPostResult> comparator =
        switch (sortKey) {
          case CREATED_AT -> Comparator.comparing(AdminBoardPostResult::createdAt).reversed();
          case POST_ID -> Comparator.comparing(AdminBoardPostResult::postId).reversed();
          case STATUS -> Comparator.comparing(AdminBoardPostResult::status);
          case TYPE -> Comparator.comparing(AdminBoardPostResult::type);
          case COMMENT_COUNT -> Comparator.comparing(AdminBoardPostResult::commentCount).reversed();
        };
    if (sortKey == AdminBoardPostSortKey.POST_ID) {
      return comparator;
    }
    return comparator.thenComparing(AdminBoardPostResult::postId, Comparator.reverseOrder());
  }

  private Page<AdminBoardPostResult> emptyPage(int page, int size) {
    return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
  }
}
