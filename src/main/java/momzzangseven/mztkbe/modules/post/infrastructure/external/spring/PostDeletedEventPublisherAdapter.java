package momzzangseven.mztkbe.modules.post.infrastructure.external.spring;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.PublishPostDeletedEventPort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostDeletedEventPublisherAdapter implements PublishPostDeletedEventPort {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void publish(PostDeletedEvent event) {
    eventPublisher.publishEvent(event);
  }
}
