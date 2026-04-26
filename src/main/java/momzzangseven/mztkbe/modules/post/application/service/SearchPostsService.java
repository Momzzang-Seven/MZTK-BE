package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsResult;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchPostsService implements SearchPostsUseCase, SearchPostsCursorUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadTagPort loadTagPort;
  private final PostListEnricher postListEnricher;

  @Override
  public SearchPostsResult searchPosts(PostSearchCondition condition, Long requesterUserId) {
    List<Long> filteredPostIds = null;
    if (StringUtils.hasText(condition.tagName())) {
      filteredPostIds = loadTagPort.findPostIdsByTagName(condition.tagName());
      if (filteredPostIds.isEmpty()) {
        return new SearchPostsResult(List.of(), false);
      }
    }

    // 1. size + 1 probe row를 포함해 게시글 조회
    List<Post> posts = postPersistencePort.findPostsByCondition(condition, filteredPostIds);
    boolean hasNext = posts.size() > condition.size();
    List<Post> pagePosts = hasNext ? posts.subList(0, condition.size()) : posts;
    if (pagePosts.isEmpty()) {
      return new SearchPostsResult(List.of(), false);
    }

    return new SearchPostsResult(postListEnricher.enrich(pagePosts, requesterUserId), hasNext);
  }

  @Override
  public SearchPostsCursorResult searchPostsByCursor(
      PostCursorSearchCondition condition, Long requesterUserId) {
    Optional<Long> tagId = Optional.empty();
    if (StringUtils.hasText(condition.tagName())) {
      tagId = loadTagPort.findTagIdByName(condition.tagName());
      if (tagId.isEmpty()) {
        return new SearchPostsCursorResult(List.of(), false, null);
      }
    }

    List<Post> posts =
        postPersistencePort.findPostsByCursorCondition(condition, tagId.orElse(null));
    boolean hasNext = posts.size() > condition.size();
    List<Post> pagePosts = hasNext ? posts.subList(0, condition.size()) : posts;
    if (pagePosts.isEmpty()) {
      return new SearchPostsCursorResult(List.of(), false, null);
    }

    String nextCursor =
        hasNext ? createNextCursor(pagePosts.get(pagePosts.size() - 1), condition) : null;
    return new SearchPostsCursorResult(
        postListEnricher.enrich(pagePosts, requesterUserId), hasNext, nextCursor);
  }

  private String createNextCursor(Post lastPost, PostCursorSearchCondition condition) {
    return CursorCodec.encode(
        new KeysetCursor(
            lastPost.getCreatedAt(), lastPost.getId(), condition.pageRequest().scope()));
  }
}
