package momzzangseven.mztkbe.modules.tag.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.tag.application.port.in.ManageTagsUseCase;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 게시글 삭제 이벤트(PostDeletedEvent)를 수신하여 연관된 태그 연결 데이터를 처리하는 핸들러. 게시글이 삭제될 때(Hard Delete), 태그 원본
 * 데이터(Tag)는 다른 게시글에서도 사용해야 하므로 유지하고, 해당 게시글과 태그의 연결 매핑 데이터(PostTag)만 DB에서 완전히 삭제(Hard Delete)합니다.
 */
@Component
@RequiredArgsConstructor
public class TagPostDeleteEventHandler {
  private final ManageTagsUseCase manageTagsUseCase;

  @EventListener
  public void handlePostDeletedEvent(PostDeletedEvent event) {
    manageTagsUseCase.deleteTagsByPostId(event.postId());
  }
}
