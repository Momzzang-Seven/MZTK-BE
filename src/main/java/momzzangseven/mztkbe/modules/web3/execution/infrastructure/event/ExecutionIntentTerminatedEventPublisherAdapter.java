package momzzangseven.mztkbe.modules.web3.execution.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class ExecutionIntentTerminatedEventPublisherAdapter
    implements PublishExecutionIntentTerminatedPort {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void publish(ExecutionIntentTerminatedEvent event) {
    eventPublisher.publishEvent(event);
  }
}
