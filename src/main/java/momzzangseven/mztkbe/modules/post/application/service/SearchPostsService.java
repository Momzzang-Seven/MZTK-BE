package momzzangseven.mztkbe.modules.post.application.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // 이게 필요합니다!

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchPostsService implements SearchPostsUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadTagPort loadTagPort;

  @Override
  public List<Post> searchPosts(PostSearchCondition condition) {
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

    // 2. 조회된 게시글들의 ID 추출
    List<Long> postIds = posts.stream().map(Post::getId).toList();

    // 3. 태그 일괄 조회
    Map<Long, List<String>> tagMap = loadTagPort.findTagsByPostIdsIn(postIds);

    // 4. 메모리에서 매핑
    posts.forEach(
        post -> {
          post.setTags(tagMap.getOrDefault(post.getId(), Collections.emptyList()));
        });

    return posts;
  }
}
