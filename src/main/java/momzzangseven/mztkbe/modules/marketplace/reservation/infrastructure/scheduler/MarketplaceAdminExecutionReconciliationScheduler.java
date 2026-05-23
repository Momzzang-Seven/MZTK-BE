package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Replays missed confirmed/terminal marketplace admin execution hooks. */
@Slf4j
@Component
@ConditionalOnMarketplaceAdminEnabled
@ConditionalOnProperty(
    prefix = "web3.marketplace.admin.reconciliation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MarketplaceAdminExecutionReconciliationScheduler {

  private final ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase reconcileUseCase;
  private final int batchSize;

  public MarketplaceAdminExecutionReconciliationScheduler(
      ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase reconcileUseCase,
      @Value("${web3.marketplace.admin.reconciliation.batch-size:100}") int batchSize) {
    this.reconcileUseCase = reconcileUseCase;
    this.batchSize = batchSize;
  }

  @Scheduled(
      cron = "${web3.marketplace.admin.reconciliation.cron:0 */5 * * * *}",
      zone = "${web3.marketplace.admin.reconciliation.zone:Asia/Seoul}")
  public void run() {
    try {
      ReconcileMarketplaceAdminTerminalExecutionAttemptResult result =
          reconcileUseCase.execute(
              new ReconcileMarketplaceAdminTerminalExecutionAttemptCommand(batchSize));
      if (!result.isEmpty()) {
        log.info(
            "marketplace admin execution hook reconciliation completed: scanned={}, replayed={}, skipped={}, failed={}",
            result.scanned(),
            result.replayed(),
            result.skipped(),
            result.failed());
      }
    } catch (RuntimeException e) {
      log.error("marketplace admin execution reconciliation scheduler failed", e);
    }
  }
}
