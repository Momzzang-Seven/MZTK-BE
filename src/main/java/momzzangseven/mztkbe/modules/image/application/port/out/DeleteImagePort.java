package momzzangseven.mztkbe.modules.image.application.port.out;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/**
 * Hexagonal Architecture: OUTPUT PORT. Abstraction for deleting image records from the persistence
 * layer. Implemented by {@code ImagePersistenceAdapter} in the infrastructure layer.
 */
public interface DeleteImagePort {
  /**
   * Deletes PENDING image records created before the given cutoff timestamp, up to batchSize rows.
   *
   * @param cutoff records with {@code created_at} before this instant are eligible for deletion
   * @param batchSize maximum number of rows to delete in a single call
   * @return the number of rows actually deleted
   */
  int deletePendingImagesBefore(Instant cutoff, int batchSize);

  /**
   * Physically deletes image records by their IDs. Called by {@code ImageUnlinkedCleanupService}
   * after the corresponding S3 objects have been deleted (or confirmed absent).
   *
   * @param ids list of image IDs to permanently remove
   */
  void deleteImagesByIdIn(List<Long> ids);

  /**
   * Unlinks all images associated with the given reference by setting referenceType and referenceId
   * to null. Does NOT physically delete the DB row or the S3 object. The
   * ImageUnlinkedCleanupScheduler handles actual cleanup asynchronously.
   *
   * <p>{@code referenceTypes} should be the result of {@link ImageReferenceType#expand()} so that
   * virtual types (e.g. MARKET_CLASS) are resolved to their concrete subtypes.
   *
   * @param referenceTypes the concrete reference type(s) to match
   * @param referenceId the owning entity ID
   */
  void unlinkImagesByReference(List<ImageReferenceType> referenceTypes, Long referenceId);

  /**
   * Unlinks specific images by ID. Used during post update to unlink only removed images.
   *
   * @param ids list of image IDs to unlink
   */
  void unlinkImagesByIdIn(List<Long> ids);
}
