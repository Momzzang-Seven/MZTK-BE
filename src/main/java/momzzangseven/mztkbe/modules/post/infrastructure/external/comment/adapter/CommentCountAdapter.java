package momzzangseven.mztkbe.modules.post.infrastructure.external.comment.adapter;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountCommentsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CountCommentsPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentCountAdapter implements CountCommentsPort {

  private final CountCommentsUseCase countCommentsUseCase;

  @Override
  public long countCommentsByPostId(Long postId) {
    return countCommentsUseCase.countCommentsByPostId(postId);
  }

  @Override
  public Map<Long, Long> countCommentsByPostIds(List<Long> postIds) {
    return countCommentsUseCase.countCommentsByPostIds(postIds);
  }
}
