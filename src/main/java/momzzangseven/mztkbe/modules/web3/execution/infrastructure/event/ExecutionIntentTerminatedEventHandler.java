package momzzangseven.mztkbe.modules.web3.execution.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunExecutionTerminationHookCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunExecutionTerminationHookUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class ExecutionIntentTerminatedEventHandler {

  private final RunExecutionTerminationHookUseCase runExecutionTerminationHookUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(ExecutionIntentTerminatedEvent event) {
    try {
      runExecutionTerminationHookUseCase.execute(RunExecutionTerminationHookCommand.from(event));
    } catch (Exception e) {
      log.error(
          "failed to run execution intent termination hook after intent commit: executionIntentId={}, terminalStatus={}, failureReason={}",
          event.executionIntentId(),
          event.terminalStatus(),
          event.failureReason(),
          e);
    }
  }
}
