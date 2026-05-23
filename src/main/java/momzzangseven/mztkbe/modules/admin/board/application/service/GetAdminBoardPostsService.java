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
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostListPolicyPort;
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
  private final LoadAdminBoardPostListPolicyPort loadAdminBoardPostListPolicyPort;

  @Override
  @AdminOnly(
      actionType = "ADMIN_BOARD_POSTS_VIEW",
      targetType = AuditTargetType.POST,
      operatorId = "#command.operatorUserId",
      targetId = "'posts'",
      audit = false)
  public Page<AdminBoardPostResult> execute(GetAdminBoardPostsCommand command) {
    if (supportsPostPagedSort(command.sortKey())) {
      return executePostPaged(command);
    }

    LoadAdminBoardPostsPort.AdminBoardPostQuery postQuery =
        new LoadAdminBoardPostsPort.AdminBoardPostQuery(
            command.search(),
            command.postId(),
            command.userId(),
            command.status(),
            command.type(),
            command.publicationStatus(),
            command.moderationStatus());
    long matchingPostCount = loadAdminBoardPostsPort.count(postQuery);
    if (matchingPostCount == 0) {
      return emptyPage(command.page(), command.size());
    }
    validateCommentCountSortScanSize(matchingPostCount);

    List<LoadAdminBoardPostsPort.AdminBoardPostView> posts =
        loadAdminBoardPostsPort.load(postQuery);
    if (posts.isEmpty()) {
      return emptyPage(command.page(), command.size());
    }
    validateCommentCountSortScanSize(posts.size());

    List<Long> postIds =
        posts.stream().map(LoadAdminBoardPostsPort.AdminBoardPostView::postId).toList();
    Map<Long, Long> commentCounts = loadAdminBoardPostCommentCountsPort.load(postIds);
    Map<Long, String> writerNicknames = loadAdminBoardWriterNicknamesPort.load(writerIds(posts));

    List<AdminBoardPostResult> combined =
        posts.stream()
            .map(post -> toResult(post, writerNicknames, commentCounts))
            .sorted(buildComparator(command.sortKey()))
            .toList();

    long offset = (long) command.page() * command.size();
    if (offset >= combined.size()) {
      return new PageImpl<>(
          List.of(), PageRequest.of(command.page(), command.size()), combined.size());
    }
    int fromIndex = (int) offset;
    int toIndex = Math.min(fromIndex + command.size(), combined.size());
    return new PageImpl<>(
        combined.subList(fromIndex, toIndex),
        PageRequest.of(command.page(), command.size()),
        combined.size());
  }

  private Page<AdminBoardPostResult> executePostPaged(GetAdminBoardPostsCommand command) {
    Page<LoadAdminBoardPostsPort.AdminBoardPostView> postPage =
        loadAdminBoardPostsPort.loadPage(
            new LoadAdminBoardPostsPort.AdminBoardPostPageQuery(
                command.search(),
                command.postId(),
                command.userId(),
                command.status(),
                command.type(),
                command.publicationStatus(),
                command.moderationStatus(),
                command.page(),
                command.size(),
                command.sortKey()));
    if (postPage.isEmpty()) {
      return emptyPage(command.page(), command.size());
    }

    List<LoadAdminBoardPostsPort.AdminBoardPostView> posts = postPage.getContent();
    List<Long> postIds =
        posts.stream().map(LoadAdminBoardPostsPort.AdminBoardPostView::postId).toList();
    Map<Long, Long> commentCounts = loadAdminBoardPostCommentCountsPort.load(postIds);
    Map<Long, String> writerNicknames = loadAdminBoardWriterNicknamesPort.load(writerIds(posts));

    List<AdminBoardPostResult> items =
        posts.stream().map(post -> toResult(post, writerNicknames, commentCounts)).toList();
    return new PageImpl<>(
        items, PageRequest.of(command.page(), command.size()), postPage.getTotalElements());
  }

  private boolean supportsPostPagedSort(AdminBoardPostSortKey sortKey) {
    return sortKey == AdminBoardPostSortKey.CREATED_AT
        || sortKey == AdminBoardPostSortKey.POST_ID
        || sortKey == AdminBoardPostSortKey.STATUS
        || sortKey == AdminBoardPostSortKey.TYPE;
  }

  /**
   * COMMENT_COUNT is supplied by the comment module through a port, so this path cannot use a
   * post-owned DB-page query without crossing module persistence boundaries.
   */
  private void validateCommentCountSortScanSize(long matchingPostCount) {
    int maxScanSize = loadAdminBoardPostListPolicyPort.maxCommentCountSortScanSize();
    if (maxScanSize < 1) {
      throw new IllegalStateException("commentCount sort max scan size must be positive");
    }
    if (matchingPostCount > maxScanSize) {
      throw new IllegalArgumentException(
          "commentCount sort can scan at most "
              + maxScanSize
              + " matching posts; narrow filters or search conditions");
    }
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
        post.publicationStatus(),
        post.moderationStatus(),
        post.title(),
        preview(post.content()),
        post.writerId(),
        writerNicknames.get(post.writerId()),
        post.createdAt(),
        commentCounts.getOrDefault(post.postId(), 0L));
  }

  private static String preview(String content) {
    if (content == null || content.codePointCount(0, content.length()) <= CONTENT_PREVIEW_LENGTH) {
      return content;
    }
    int endIndex = content.offsetByCodePoints(0, CONTENT_PREVIEW_LENGTH);
    return content.substring(0, endIndex);
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
