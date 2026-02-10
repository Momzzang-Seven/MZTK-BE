package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPostService implements GetPostUseCase {

  private final PostPersistencePort postPersistencePort;

  @Override
  public PostResult getPost(Long postId) {
    Post post =
        postPersistencePort
            .loadPost(postId)
            .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다. ID: " + postId));

    return PostResult.fromDomain(post);
  }
}
