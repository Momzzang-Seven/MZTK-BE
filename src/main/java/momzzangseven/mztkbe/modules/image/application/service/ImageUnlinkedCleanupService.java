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
import momzzangseven.mztkbe.modules.image.application.port.out.LoadUnlinkedImageCleanupPolicyPort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that permanently removes non-PENDING images with {@code referenceId = null}
 * in fixed-size batches.
 *
 * <p>An image's {@code referenceId} is set to {@code null} when its owning reference is deleted or
 * updated. The image's {@code status} and {@code referenceType} are intentionally preserved:
 *
 * <ul>
 *   <li>PENDING images remain available for an in-flight Lambda callback and are handled separately
 *       by {@code ImagePendingCleanupService} — this service ignores them.
 *   <li>COMPLETED/FAILED images with {@code referenceId = null} are cleaned up here after the
 *       retention window (default 5 hours). The {@code referenceType} is preserved so the image can
 *       still be re-linked by the same user before the window expires.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUnlinkedCleanupService implements RunUnlinkedImageCleanupBatchUseCase {
  private final LoadImagePort loadImagePort;
  private final DeleteImagePort deleteImagePort;
  private final DeleteS3ObjectPort deleteS3ObjectPort;
  private final LoadUnlinkedImageCleanupPolicyPort cleanupPolicyPort;

  /**
   * Processes one batch of non-PENDING images with {@code referenceId = null} that are older than
   * the retention window. For each candidate the final S3 object is deleted if present, then the DB
   * row is permanently removed.
   *
   * @param now reference time used to compute the cutoff (injected for testability)
   * @return number of records deleted; {@code 0} signals that no more work remains
   */
  @Override
  @Transactional
  public int runBatch(Instant now) {
    Instant cutoff = now.minus(cleanupPolicyPort.getRetentionHours(), ChronoUnit.HOURS);
    List<Image> candidates =
        loadImagePort.findUnlinkedImagesBefore(cutoff, cleanupPolicyPort.getBatchSize());

    if (candidates.isEmpty()) {
      return 0;
    }

    for (Image image : candidates) {
      if (image.getFinalObjectKey() != null) {
        // The final (WebP) object in S3 must be removed explicitly;
        // it is not covered by any S3 lifecycle rule on the tmp/ prefix.
        // Images that were still PENDING when unlinked have no finalObjectKey, so their
        // tmp/ object is reclaimed by the bucket's 1-day lifecycle rule automatically.
        deleteS3ObjectPort.deleteObject(image.getFinalObjectKey());
      }
    }

    List<Long> ids = candidates.stream().map(Image::getId).toList();
    deleteImagePort.deleteImagesByIdIn(ids);

    log.info("Unlinked image cleanup batch: deleted={}, cutoff={}", candidates.size(), cutoff);
    return candidates.size();
  }
}
