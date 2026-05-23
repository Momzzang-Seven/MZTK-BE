package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayTerminatedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ReplayTerminatedExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

final class TransactionalReplayTerminatedExecutionIntentUseCase
    implements ReplayTerminatedExecutionIntentUseCase {

  private final ReplayTerminatedExecutionIntentService delegate;
  private final TransactionTemplate transactionTemplate;

  TransactionalReplayTerminatedExecutionIntentUseCase(
      ReplayTerminatedExecutionIntentService delegate,
      PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public boolean execute(ReplayTerminatedExecutionIntentCommand command) {
    ExecutionIntent intent =
        transactionTemplate.execute(status -> delegate.resolveReplayTarget(command));
    if (intent == null) {
      return false;
    }
    return delegate.replayTerminated(intent);
  }
}
