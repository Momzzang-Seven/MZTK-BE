package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerDeletedEventPublisherAdapter implements PublishAnswerDeletedEventPort {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void publish(AnswerDeletedEvent event) {
    eventPublisher.publishEvent(event);
  }
}
