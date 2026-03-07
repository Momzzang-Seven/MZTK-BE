package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPostAdapter implements LoadPostPort {

  private final PostJpaRepository postJpaRepository;

  @Override
  public Optional<PostContext> loadPost(Long postId) {
    return postJpaRepository
        .findById(postId)
        .map(
            postEntity ->
                new PostContext(
                    postEntity.getId(), postEntity.getUserId(), postEntity.getIsSolved()));
  }
}
