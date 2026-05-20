package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Slf4j
public class ReservationPostCommitTransactionAdapter implements RunReservationPostCommitPort {

  private final TransactionTemplate requiresNewTemplate;

  public ReservationPostCommitTransactionAdapter(PlatformTransactionManager transactionManager) {
    this.requiresNewTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void afterCommit(String callbackName, Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      runSafely(callbackName, action);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            runSafely(callbackName, action);
          }
        });
  }

  @Override
  public void requiresNew(Runnable action) {
    requiresNewTemplate.executeWithoutResult(status -> action.run());
  }

  private void runSafely(String callbackName, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.error("{} after-commit callback failed", callbackName, e);
    }
  }
}
