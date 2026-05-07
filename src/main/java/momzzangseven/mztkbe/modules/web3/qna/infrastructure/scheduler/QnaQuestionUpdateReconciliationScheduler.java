package momzzangseven.mztkbe.modules.web3.qna.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaQuestionUpdateReconciliationUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnUserExecutionEnabled
@ConditionalOnProperty(
    prefix = "web3.qna.question-update-reconciliation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class QnaQuestionUpdateReconciliationScheduler {

  private final RunQnaQuestionUpdateReconciliationUseCase reconciliationUseCase;
  private final int batchSize;

  public QnaQuestionUpdateReconciliationScheduler(
      RunQnaQuestionUpdateReconciliationUseCase reconciliationUseCase,
      @Value("${web3.qna.question-update-reconciliation.batch-size:100}") int batchSize) {
    this.reconciliationUseCase = reconciliationUseCase;
    this.batchSize = batchSize;
  }

  @Scheduled(
      cron = "${web3.qna.question-update-reconciliation.cron:0 */5 * * * *}",
      zone = "${web3.qna.question-update-reconciliation.zone:Asia/Seoul}")
  public void run() {
    int scannedTotal = 0;
    int repairedTotal = 0;
    int skippedTotal = 0;
    int failedTotal = 0;

    try {
      while (true) {
        RunQnaQuestionUpdateReconciliationResult result =
            reconciliationUseCase.run(new RunQnaQuestionUpdateReconciliationCommand(batchSize));
        scannedTotal += result.scanned();
        repairedTotal += result.repaired();
        skippedTotal += result.skipped();
        failedTotal += result.failed();
        if (result.scanned() < batchSize || result.skipped() > 0 || result.failed() > 0) {
          break;
        }
      }
    } catch (RuntimeException e) {
      log.error("qna question update reconciliation scheduler failed", e);
      return;
    }

    if (scannedTotal > 0 || repairedTotal > 0 || skippedTotal > 0 || failedTotal > 0) {
      log.info(
          "qna question update reconciliation completed: scanned={}, repaired={}, skipped={}, failed={}",
          scannedTotal,
          repairedTotal,
          skippedTotal,
          failedTotal);
    }
  }
}
