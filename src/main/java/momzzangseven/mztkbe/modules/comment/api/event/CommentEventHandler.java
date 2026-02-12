package momzzangseven.mztkbe.modules.comment.api.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.in.DeleteCommentUseCase;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentEventHandler {
  private final DeleteCommentUseCase deleteCommentUseCase;

  @EventListener
  public void handlePostDeletedEvent(PostDeletedEvent event) {
    deleteCommentUseCase.deleteCommentsByPostId(event.postId());
  }
}
