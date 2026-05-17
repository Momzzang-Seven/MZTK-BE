package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Slf4j
public class SpringRunAfterCommitAdapter implements RunAfterCommitPort {

  private final TransactionTemplate transactionTemplate;
  private final ThreadLocal<Boolean> runningAfterCommitCallback =
      ThreadLocal.withInitial(() -> false);

  public SpringRunAfterCommitAdapter(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void runAfterCommit(Runnable action) {
    if (canRegisterAfterCommitCallback()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              runAfterCommitCallback(() -> runInNewTransaction(action));
            }
          });
      return;
    }

    runInNewTransaction(action);
  }

  @Override
  public void runAfterCommitWithoutTransaction(Runnable action) {
    if (canRegisterAfterCommitCallback()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              runAfterCommitCallback(() -> runWithoutTransaction(action));
            }
          });
      return;
    }

    runWithoutTransaction(action);
  }

  private boolean canRegisterAfterCommitCallback() {
    return TransactionSynchronizationManager.isSynchronizationActive()
        && !Boolean.TRUE.equals(runningAfterCommitCallback.get());
  }

  private void runAfterCommitCallback(Runnable action) {
    Boolean previous = runningAfterCommitCallback.get();
    runningAfterCommitCallback.set(true);
    try {
      action.run();
    } finally {
      runningAfterCommitCallback.set(previous);
    }
  }

  private void runInNewTransaction(Runnable action) {
    try {
      transactionTemplate.executeWithoutResult(status -> action.run());
    } catch (RuntimeException exception) {
      log.error("failed to run after-commit action in a new transaction", exception);
    }
  }

  private void runWithoutTransaction(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      log.error("failed to run after-commit action", exception);
    }
  }
}
