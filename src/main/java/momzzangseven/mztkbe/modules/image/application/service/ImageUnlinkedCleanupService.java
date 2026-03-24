package momzzangseven.mztkbe.modules.image.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.port.in.RunUnlinkedImageCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.image.infrastructure.config.ImageUnlinkedCleanupProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that permanently removes unlinked image records (reference_type IS NULL AND
 * reference_id IS NULL) in fixed-size batches.
 *
 * <p>An image becomes unlinked when:
 *
 * <ul>
 *   <li>A post is deleted — all associated images are unlinked via {@code PostDeletedEvent}.
 *   <li>A post is updated — removed images are unlinked by {@code UpsertImagesByReferenceService}.
 *   <li>A presigned URL is issued but the post is never created within the retention window.
 * </ul>
 *
 * <p>The retention window (default 5 hours) provides enough buffer for Lambda to finish processing
 * (max Lambda timeout is 15 minutes) before the record is permanently removed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUnlinkedCleanupService implements RunUnlinkedImageCleanupBatchUseCase {
  private final LoadImagePort loadImagePort;
  private final DeleteImagePort deleteImagePort;
  private final DeleteS3ObjectPort deleteS3ObjectPort;
  private final ImageUnlinkedCleanupProperties props;

  /**
   * Processes one batch of unlinked images older than the retention window.
   *
   * @param now reference time used to compute the cutoff (injected for testability)
   * @return number of records deleted; {@code 0} signals that no more work remains
   */
  @Override
  @Transactional
  public int runBatch(Instant now) {
    Instant cutoff = now.minus(props.getRetentionHours(), ChronoUnit.HOURS);
    List<Image> candidates = loadImagePort.findUnlinkedImagesBefore(cutoff, props.getBatchSize());

    if (candidates.isEmpty()) {
      return 0;
    }

    for (Image image : candidates) {
      if (image.getStatus() == ImageStatus.COMPLETED && image.getFinalObjectKey() != null) {
        // The final (WebP) object in S3 must be removed explicitly;
        // it is not covered by any S3 lifecycle rule on the tmp/ prefix.
        deleteS3ObjectPort.deleteObject(image.getFinalObjectKey());
      }
      // PENDING / FAILED: the tmp S3 object resides under tmp/ and is reclaimed
      // by the bucket's 1-day lifecycle rule — no explicit deletion needed here.
    }

    List<Long> ids = candidates.stream().map(Image::getId).toList();
    deleteImagePort.deleteImagesByIdIn(ids);

    log.info("Unlinked image cleanup batch: deleted={}, cutoff={}", candidates.size(), cutoff);
    return candidates.size();
  }
}
