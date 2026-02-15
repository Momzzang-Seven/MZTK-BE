package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
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
public class SearchPostsService implements SearchPostsUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadTagPort loadTagPort;

  @Override
  public List<Post> searchPosts(PostSearchCondition condition) {
    List<Long> filteredPostIds = null;

    if (StringUtils.hasText(condition.getTagName())) {
      filteredPostIds = loadTagPort.findPostIdsByTagName(condition.getTagName());
      if (filteredPostIds.isEmpty()) return List.of();
    }

    // 2. 게시글 기본 정보 조회
    List<Post> posts = postPersistencePort.findPostsByCondition(condition, filteredPostIds);

    // 3.조회된 게시글들에 대한 태그 정보를 가져와서 채워넣기
    for (Post post : posts) {
      List<String> tags = loadTagPort.findTagNamesByPostId(post.getId());
      post.setTags(tags); // Post 도메인 객체에 태그 리스트 세팅
    }

    return posts;
  }
}
