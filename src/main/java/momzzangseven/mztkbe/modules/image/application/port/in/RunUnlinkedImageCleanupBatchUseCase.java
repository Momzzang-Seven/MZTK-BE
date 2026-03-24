package momzzangseven.mztkbe.modules.image.application.port.in;

import java.time.Instant;

/**
 * Input port for running one batch of unlinked-image cleanup. Implemented by {@code
 * ImageUnlinkedCleanupService} and triggered by the infrastructure-layer scheduler.
 */
public interface RunUnlinkedImageCleanupBatchUseCase {

  /**
   * Processes one batch of unlinked images older than the retention window.
   *
   * @param now reference time used to compute the cutoff (injected for testability)
   * @return number of records deleted; {@code 0} signals that no more work remains
   */
  int runBatch(Instant now);
}
