package momzzangseven.mztkbe.modules.answer.infrastructure.external.post.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPostAdapter implements LoadPostPort {

  private final GetPostContextUseCase getPostContextUseCase;

  @Override
  public Optional<PostContext> loadPost(Long postId) {
    return getPostContextUseCase
        .getPostContext(postId)
        .map(
            post ->
                new PostContext(
                    post.postId(),
                    post.writerId(),
                    post.solved(),
                    post.questionPost(),
                    post.content(),
                    post.reward()));
  }
}
