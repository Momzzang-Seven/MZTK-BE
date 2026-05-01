package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;

public interface PublishExecutionIntentTerminatedPort {

  void publish(ExecutionIntentTerminatedEvent event);
}
