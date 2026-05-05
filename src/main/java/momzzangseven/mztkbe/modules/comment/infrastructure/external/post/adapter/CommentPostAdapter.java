package momzzangseven.mztkbe.modules.comment.infrastructure.external.post.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentPostAdapter implements LoadPostPort {

  private final GetPostContextUseCase getPostContextUseCase;

  @Override
  public Optional<PostVisibilityContext> loadPostVisibilityContext(Long postId) {
    return getPostContextUseCase
        .getPostContext(postId)
        .map(
            post ->
                new PostVisibilityContext(post.postId(), post.writerId(), post.publiclyVisible()));
  }
}
