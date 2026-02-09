package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPostService implements GetPostUseCase {
  private final LoadPostPort loadPostPort;

  @Override
  @Transactional
  public Post getPost(Long postId) {
    Post post =
        loadPostPort
            .loadPost(postId)
            .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다. ID: " + postId));

    post.increaseViewCount();

    return post;
  }
}
