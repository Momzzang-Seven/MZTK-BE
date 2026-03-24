package momzzangseven.mztkbe.modules.image.application.port.in;

import java.time.Instant;

/**
 * Input port for running one batch of orphaned-PENDING-image cleanup. Implemented by {@code
 * ImagePendingCleanupService} and triggered by the infrastructure-layer scheduler.
 */
public interface RunPendingImageCleanupBatchUseCase {

  /**
   * Deletes one batch of orphaned PENDING image records.
   *
   * @param now the reference time for computing the cutoff (passed in for testability)
   * @return the number of rows deleted; {@code 0} signals that no more work remains
   */
  int runBatch(Instant now);
}
