package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverExpiredMarketplaceAdminExecutionAttemptUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Maintenance scheduler for expired, unbound marketplace admin execution preparations. */
@Slf4j
@Component
@ConditionalOnMarketplaceAdminEnabled
@ConditionalOnProperty(
    prefix = "web3.marketplace.admin.recovery",
    name = "enabled",
    havingValue = "true")
public class MarketplaceAdminExecutionAttemptMaintenanceScheduler {

  private final RecoverExpiredMarketplaceAdminExecutionAttemptUseCase recoverUseCase;
  private final ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase reconcileUseCase;
  private final Clock clock;
  private final int batchSize;

  public MarketplaceAdminExecutionAttemptMaintenanceScheduler(
      RecoverExpiredMarketplaceAdminExecutionAttemptUseCase recoverUseCase,
      ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase reconcileUseCase,
      Clock clock,
      @Value("${web3.marketplace.admin.recovery.batch-size:100}") int batchSize) {
    this.recoverUseCase = recoverUseCase;
    this.reconcileUseCase = reconcileUseCase;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  @Scheduled(
      cron = "${web3.marketplace.admin.recovery.cron:0 */5 * * * *}",
      zone = "${web3.marketplace.admin.recovery.zone:Asia/Seoul}")
  public void run() {
    try {
      int recoveredTotal = 0;
      int skippedTotal = 0;
      int failedTotal = 0;
      while (true) {
        RecoverExpiredMarketplaceAdminExecutionAttemptResult result =
            recoverUseCase.execute(
                new RecoverExpiredMarketplaceAdminExecutionAttemptCommand(
                    LocalDateTime.now(clock), batchSize));
        recoveredTotal += result.recovered();
        skippedTotal += result.skipped();
        failedTotal += result.failed();
        if (result.scanned() < batchSize || result.failed() > 0) {
          break;
        }
      }
      if (recoveredTotal > 0 || skippedTotal > 0 || failedTotal > 0) {
        log.info(
            "marketplace admin recovery completed: recovered={}, skipped={}, failed={}",
            recoveredTotal,
            skippedTotal,
            failedTotal);
      }
      ReconcileMarketplaceAdminTerminalExecutionAttemptResult reconcileResult =
          reconcileUseCase.execute(
              new ReconcileMarketplaceAdminTerminalExecutionAttemptCommand(batchSize));
      if (!reconcileResult.isEmpty()) {
        log.info(
            "marketplace admin terminal hook reconciliation completed: scanned={}, replayed={}, skipped={}, failed={}",
            reconcileResult.scanned(),
            reconcileResult.replayed(),
            reconcileResult.skipped(),
            reconcileResult.failed());
      }
    } catch (RuntimeException e) {
      log.error("marketplace admin recovery scheduler failed", e);
    }
  }
}
