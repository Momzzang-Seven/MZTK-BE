package momzzangseven.mztkbe.modules.account.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.service.ExternalDisconnectRetryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalDisconnectRetryScheduler {

  private final ExternalDisconnectRetryService retryService;

  /** Run external disconnect retry batch on a fixed delay. */
  @Scheduled(fixedDelayString = "${withdrawal.external-disconnect.fixed-delay}")
  public void run() {
    int processed = retryService.runBatch();
    if (processed > 0) {
      log.info("External disconnect retry batch processed: count={}", processed);
    }
  }
}
