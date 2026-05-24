package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.service.ReplayConfirmedExecutionIntentService;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionalReplayConfirmedExecutionIntentUseCase
    implements ReplayConfirmedExecutionIntentUseCase {

  private final ReplayConfirmedExecutionIntentService delegate;
  private final TransactionTemplate transactionTemplate;

  TransactionalReplayConfirmedExecutionIntentUseCase(
      ReplayConfirmedExecutionIntentService delegate,
      PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public boolean execute(ReplayConfirmedExecutionIntentCommand command) {
    ExecutionIntent intent =
        transactionTemplate.execute(status -> delegate.resolveReplayTarget(command));
    if (intent == null) {
      return false;
    }
    return Boolean.TRUE.equals(
        transactionTemplate.execute(status -> delegate.replayConfirmed(intent)));
  }
}
