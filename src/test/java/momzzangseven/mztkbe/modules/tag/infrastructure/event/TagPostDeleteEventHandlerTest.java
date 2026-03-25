package momzzangseven.mztkbe.modules.tag.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.tag.application.port.in.ManageTagsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagPostDeleteEventHandler unit test")
class TagPostDeleteEventHandlerTest {

  @Mock private ManageTagsUseCase manageTagsUseCase;

  @InjectMocks private TagPostDeleteEventHandler handler;

  @Test
  @DisplayName("delegates post tag cleanup with event postId")
  void handlePostDeletedEvent_callsDeleteTagsByPostIdWithEventPostId() {
    PostDeletedEvent event = new PostDeletedEvent(11L, PostType.FREE);

    handler.handlePostDeletedEvent(event);

    verify(manageTagsUseCase).deleteTagsByPostId(event.postId());
  }

  @Test
  @DisplayName("propagates exception because tag cleanup stays synchronous")
  void handlePostDeletedEvent_propagatesException() {
    PostDeletedEvent event = new PostDeletedEvent(21L, PostType.QUESTION);
    RuntimeException failure = new RuntimeException("db fail");
    doThrow(failure).when(manageTagsUseCase).deleteTagsByPostId(21L);

    assertThatThrownBy(() -> handler.handlePostDeletedEvent(event)).isSameAs(failure);
    verify(manageTagsUseCase).deleteTagsByPostId(21L);
  }
}
