package momzzangseven.mztkbe.modules.web3.qna.infrastructure.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaAutoAcceptBatchResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaAutoAcceptBatchUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnInternalExecutionEnabled
@ConditionalOnProperty(prefix = "web3.qna.auto-accept", name = "enabled", havingValue = "true")
public class QnaAutoAcceptScheduler {

  private final RunQnaAutoAcceptBatchUseCase runQnaAutoAcceptBatchUseCase;

  @Scheduled(cron = "#{@qnaAutoAcceptProperties.cron}", zone = "#{@qnaAutoAcceptProperties.zone}")
  public void run() {
    try {
      int scheduledTotal = 0;
      int skippedTotal = 0;
      int failedTotal = 0;

      while (true) {
        RunQnaAutoAcceptBatchResult result = runQnaAutoAcceptBatchUseCase.runBatch(Instant.now());
        scheduledTotal += result.scheduledCount();
        skippedTotal += result.skippedCount();
        failedTotal += result.failedCount();
        if (result.isEmpty() || result.skippedCount() > 0 || result.failedCount() > 0) {
          break;
        }
      }

      if (scheduledTotal > 0 || skippedTotal > 0 || failedTotal > 0) {
        log.info(
            "qna auto-accept scheduler completed: scheduled={}, skipped={}, failed={}",
            scheduledTotal,
            skippedTotal,
            failedTotal);
      }
    } catch (RuntimeException e) {
      log.error("qna auto-accept scheduler failed", e);
    }
  }
}
