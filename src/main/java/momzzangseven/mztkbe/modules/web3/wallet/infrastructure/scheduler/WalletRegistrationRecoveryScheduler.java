package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RunWalletRegistrationRecoveryBatchUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletRegistrationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled driver for wallet registration recovery reconciliation. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRegistrationRecoveryScheduler {

  private final RunWalletRegistrationRecoveryBatchUseCase recoveryBatchUseCase;
  private final WalletRegistrationProperties registrationProperties;

  @Scheduled(
      cron = "${web3.wallet.registration.recovery.cron}",
      zone = "${web3.wallet.registration.recovery.zone}")
  public void run() {
    int batchSize = registrationProperties.getRecovery().getBatchSize();
    try {
      RunWalletRegistrationRecoveryBatchResult total = runUntilDrained(batchSize);
      if (total.scanned() > 0 || total.failed() > 0) {
        log.info(
            "wallet registration recovery completed: scanned={}, recovered={}, skipped={}, failed={}",
            total.scanned(),
            total.recovered(),
            total.skipped(),
            total.failed());
      }
    } catch (RuntimeException exception) {
      log.error("wallet registration recovery scheduler failed", exception);
    }
  }

  private RunWalletRegistrationRecoveryBatchResult runUntilDrained(int batchSize) {
    int scanned = 0;
    int recovered = 0;
    int skipped = 0;
    int failed = 0;

    while (true) {
      RunWalletRegistrationRecoveryBatchResult result =
          recoveryBatchUseCase.execute(new RunWalletRegistrationRecoveryBatchCommand(batchSize));
      scanned += result.scanned();
      recovered += result.recovered();
      skipped += result.skipped();
      failed += result.failed();

      if (result.scanned() <= 0 || result.scanned() < batchSize || result.failed() > 0) {
        break;
      }
    }

    return new RunWalletRegistrationRecoveryBatchResult(scanned, recovered, skipped, failed);
  }
}
