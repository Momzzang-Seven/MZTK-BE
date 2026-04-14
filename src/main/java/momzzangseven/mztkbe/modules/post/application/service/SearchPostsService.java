package momzzangseven.mztkbe.modules.post.application.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchPostsService implements SearchPostsUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadTagPort loadTagPort;
  private final LoadPostWriterPort loadPostWriterPort;
  private final PostLikePersistencePort postLikePersistencePort;

  @Override
  public List<PostListResult> searchPosts(PostSearchCondition condition, Long requesterUserId) {
    List<Long> filteredPostIds = null;
    if (StringUtils.hasText(condition.tagName())) {
      filteredPostIds = loadTagPort.findPostIdsByTagName(condition.tagName());
      if (filteredPostIds.isEmpty()) {
        return List.of();
      }
    }

    // 1. 게시글 조회
    List<Post> posts = postPersistencePort.findPostsByCondition(condition, filteredPostIds);
    if (posts.isEmpty()) return List.of();

    // 2. 게시글 ID 목록 추출
    List<Long> postIds = posts.stream().map(Post::getId).toList();

    // 3. 태그 일괄 조회
    Map<Long, List<String>> tagMap = loadTagPort.findTagsByPostIdsIn(postIds);

    // 4. 작성자 일괄 조회
    Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
    Map<Long, WriterSummary> writerMap = loadPostWriterPort.loadWritersByIds(userIds);
    Map<Long, Long> likeCounts =
        postLikePersistencePort.countByTargetIds(PostLikeTargetType.POST, postIds);
    Set<Long> likedPostIds =
        postLikePersistencePort.findLikedTargetIds(
            PostLikeTargetType.POST, postIds, requesterUserId);

    // 5. 메모리에서 PostListResult 조립
    return posts.stream()
        .map(
            post -> {
              List<String> tags = tagMap.getOrDefault(post.getId(), Collections.emptyList());
              WriterSummary writer = writerMap.get(post.getUserId());
              String nickname = writer != null ? writer.nickname() : null;
              String profileImageUrl = writer != null ? writer.profileImageUrl() : null;
              long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
              boolean liked = likedPostIds.contains(post.getId());
              return PostListResult.fromDomain(
                  post.withTags(tags), likeCount, liked, nickname, profileImageUrl);
            })
        .toList();
  }
}
