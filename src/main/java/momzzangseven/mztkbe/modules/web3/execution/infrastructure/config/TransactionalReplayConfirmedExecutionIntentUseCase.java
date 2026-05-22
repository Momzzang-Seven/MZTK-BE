package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionalReplayConfirmedExecutionIntentUseCase
    implements ReplayConfirmedExecutionIntentUseCase {

  private final ReplayConfirmedExecutionIntentUseCase delegate;
  private final TransactionTemplate transactionTemplate;

  TransactionalReplayConfirmedExecutionIntentUseCase(
      ReplayConfirmedExecutionIntentUseCase delegate,
      PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public boolean execute(ReplayConfirmedExecutionIntentCommand command) {
    return Boolean.TRUE.equals(transactionTemplate.execute(status -> delegate.execute(command)));
  }
}
