package momzzangseven.mztkbe.modules.tag.infrastructure.event;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.tag.application.port.in.ManageTagsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagPostDeleteEventHandlerTest {

  @Mock private ManageTagsUseCase manageTagsUseCase;

  @InjectMocks private TagPostDeleteEventHandler handler;

  @Test
  void handlePostDeletedEvent_callsDeleteTagsByPostIdWithEventPostId() {
    PostDeletedEvent event = new PostDeletedEvent(11L, PostType.FREE);

    handler.handlePostDeletedEvent(event);

    verify(manageTagsUseCase).deleteTagsByPostId(event.postId());
  }
}
