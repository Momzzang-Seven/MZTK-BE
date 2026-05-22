package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ResolveExecutionIntentRecoveryTargetUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionalResolveExecutionIntentRecoveryTargetUseCase
    implements ResolveExecutionIntentRecoveryTargetUseCase {

  private final ResolveExecutionIntentRecoveryTargetUseCase delegate;
  private final TransactionTemplate transactionTemplate;

  TransactionalResolveExecutionIntentRecoveryTargetUseCase(
      ResolveExecutionIntentRecoveryTargetUseCase delegate,
      PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setReadOnly(true);
  }

  @Override
  public Optional<ResolveExecutionIntentRecoveryTargetResult> execute(
      ResolveExecutionIntentRecoveryTargetQuery query) {
    return transactionTemplate.execute(status -> delegate.execute(query));
  }
}
