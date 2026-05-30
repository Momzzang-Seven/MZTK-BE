package momzzangseven.mztkbe.modules.account.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.in.ReconcileAccountStatusRegistryUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically rebuilds the in-memory account-status denylist from the authoritative DB snapshot.
 *
 * <p>The reconcile use case is fail-soft (it logs and swallows internally), so this scheduler only
 * delegates. Gated by {@code account.status-registry.enabled} so it can be disabled in tests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "account.status-registry.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AccountStatusRegistryReconciliationScheduler {

  private final ReconcileAccountStatusRegistryUseCase reconcileUseCase;

  @Scheduled(
      cron = "${account.status-registry.reconcile.cron:0 */5 * * * *}",
      zone = "${account.status-registry.reconcile.zone:Asia/Seoul}")
  public void run() {
    log.debug("Account status registry reconcile scheduled run starting");
    reconcileUseCase.reconcile();
  }
}
