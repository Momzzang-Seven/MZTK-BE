package momzzangseven.mztkbe.modules.account.infrastructure.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.service.ExternalDisconnectCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExternalDisconnectCleanupScheduler {

  private final ExternalDisconnectCleanupService cleanupService;

  /** Cleanup old SUCCESS/FAILED rows based on retention. */
  @Scheduled(fixedDelayString = "${withdrawal.external-disconnect.cleanup.fixed-delay}")
  public void run() {
    cleanupService.cleanup(Instant.now());
  }
}
