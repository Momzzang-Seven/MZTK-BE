package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter; // 현재 위치 유지

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.domain.event.CommentsHardDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentEventAdapter {

  @EventListener
  public void handleCommentsHardDeleted(CommentsHardDeletedEvent event) {
    cleanUpRelatedData(event.commentIds());
  }

  private void cleanUpRelatedData(List<Long> commentIds) {}
}
