package momzzangseven.mztkbe.modules.answer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.DeleteAnswersByPostUseCase;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPostDeletedEventHandler {

  private final DeleteAnswersByPostUseCase deleteAnswersByPostUseCase;

  @EventListener
  public void handle(PostDeletedEvent event) {
    deleteAnswersByPostUseCase.deleteByPostId(event.postId());
  }
}
