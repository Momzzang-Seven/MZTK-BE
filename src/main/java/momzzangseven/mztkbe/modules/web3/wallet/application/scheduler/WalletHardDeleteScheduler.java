package momzzangseven.mztkbe.modules.web3.wallet.application.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.service.WalletHardDeleteService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wallet hard delete scheduler
 *
 * <p>Runs daily to delete UNLINKED wallets after retention period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletHardDeleteScheduler {

  private final WalletHardDeleteService walletHardDeleteService;

  /**
   * Hard delete UNLINKED wallets after retention period
   *
   * <p>Runs in batches to avoid long locks and excessive memory usage.
   */
  @Scheduled(cron = "${web3.wallet.hard-delete.cron}", zone = "${web3.wallet.hard-delete.zone}")
  public void run() {
    Instant now = Instant.now();
    int totalDeleted = 0;

    while (true) {
      int deleted = walletHardDeleteService.runBatch(now);
      if (deleted <= 0) {
        break;
      }
      totalDeleted += deleted;
    }

    if (totalDeleted > 0) {
      log.info("Wallet hard delete job completed: deletedWallets={}", totalDeleted);
    }
  }
}
