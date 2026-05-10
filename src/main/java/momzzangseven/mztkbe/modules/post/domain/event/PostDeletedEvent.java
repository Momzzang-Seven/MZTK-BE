package momzzangseven.mztkbe.modules.post.domain.event;

import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostDeletedEvent(Long postId, PostType postType, List<Long> answerIds) {

  public PostDeletedEvent(Long postId, PostType postType) {
    this(postId, postType, List.of());
  }

  public PostDeletedEvent {
    answerIds = answerIds == null ? List.of() : List.copyOf(answerIds);
  }
}
