package momzzangseven.mztkbe.modules.image.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.config.ImagePendingCleanupProperties;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that deletes orphaned PENDING image records in fixed-size batches.
 *
 * <p>A PENDING record is considered orphaned when no Lambda callback has arrived within {@code
 * retentionHours} hours of creation, typically because the S3 upload never succeeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagePendingCleanupService {
  private final DeleteImagePort deleteImagePort;
  private final ImagePendingCleanupProperties props;

  /**
   * Deletes one batch of orphaned PENDING image records.
   *
   * @param now the reference time for computing the cutoff (passed in for testability)
   * @return the number of rows deleted; {@code 0} signals that no more work remains
   */
  @Transactional
  public int runBatch(Instant now) {
    Instant cutoff = now.minus(props.getRetentionHours(), ChronoUnit.HOURS);
    int deleted = deleteImagePort.deletePendingImagesBefore(cutoff, props.getBatchSize());

    if (deleted > 0) {
      log.info("Orphaned PENDING image cleanup batch: deleted={}, cutoff={}", deleted, cutoff);
    }
    return deleted;
  }
}
