package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.PublishQuestionRewardIntentEventPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentCanceledEvent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionRewardIntentEventPublisherAdapter
    implements PublishQuestionRewardIntentEventPort {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void publishRequested(QuestionRewardIntentRequestedEvent event) {
    eventPublisher.publishEvent(event);
  }

  @Override
  public void publishCanceled(QuestionRewardIntentCanceledEvent event) {
    eventPublisher.publishEvent(event);
  }
}
