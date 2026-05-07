package momzzangseven.mztkbe.modules.answer.infrastructure.scheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.port.in.CleanupAnswerPreparationUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerPreparationCleanupScheduler {

  private final CleanupAnswerPreparationUseCase cleanupAnswerPreparationUseCase;

  @Scheduled(fixedDelayString = "${answer.lifecycle.cleanup.fixed-delay-ms:300000}")
  public void cleanupExpiredPreparations() {
    try {
      var result =
          cleanupAnswerPreparationUseCase.cleanupExpiredPreparations(
              LocalDateTime.now(), cleanupBatchSize());
      if (result.total() > 0) {
        log.info(
            "answer preparation cleanup completed: createDeleted={}, deleteExpired={}, updateExpired={}",
            result.createReservationsDeleted(),
            result.deletePreparationsExpired(),
            result.updatePreparationsExpired());
      }
    } catch (Exception e) {
      log.error("answer preparation cleanup failed", e);
    }
  }

  private int cleanupBatchSize() {
    return 100;
  }
}
