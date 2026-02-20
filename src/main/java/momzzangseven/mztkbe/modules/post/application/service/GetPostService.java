package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPostService implements GetPostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadTagPort loadTagPort;

  @Override
  public PostResult getPost(Long postId) {
    Post post = postPersistencePort.loadPost(postId).orElseThrow(PostNotFoundException::new);

    List<String> tags = loadTagPort.findTagNamesByPostId(postId);
    post = post.withTags(tags);

    return PostResult.fromDomain(post);
  }
}
