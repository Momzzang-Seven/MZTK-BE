package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPostAdapter implements LoadPostPort {

  private final PostPersistencePort postPersistencePort;

  @Override
  public Optional<PostContext> loadPost(Long postId) {
    return postPersistencePort
        .loadPost(postId)
        .map(
            post ->
                new PostContext(
                    post.getId(),
                    post.getUserId(),
                    Boolean.TRUE.equals(post.getIsSolved()),
                    PostType.QUESTION.equals(post.getType())));
  }

  @Override
  public boolean existsPost(Long postId) {
    return postPersistencePort.existsPost(postId);
  }
}
