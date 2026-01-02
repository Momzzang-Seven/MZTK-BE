package momzzangseven.mztkbe.modules.user.application.scheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.service.ExternalDisconnectCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExternalDisconnectCleanupScheduler {

  private final ExternalDisconnectCleanupService cleanupService;

  /** Cleanup old SUCCESS/FAILED rows based on retention. */
  @Scheduled(fixedDelayString = "${withdrawal.external-disconnect.cleanup.fixed-delay}")
  public void run() {
    cleanupService.cleanup(LocalDateTime.now());
  }
}
