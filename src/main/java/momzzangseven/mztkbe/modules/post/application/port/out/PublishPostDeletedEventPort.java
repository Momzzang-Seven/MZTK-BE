package momzzangseven.mztkbe.modules.post.application.port.out;

import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;

public interface PublishPostDeletedEventPort {
  void publish(PostDeletedEvent event);
}
