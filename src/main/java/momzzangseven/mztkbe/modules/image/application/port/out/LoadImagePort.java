package momzzangseven.mztkbe.modules.image.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

public interface LoadImagePort {
  Optional<Image> findByTmpObjectKey(String tmpObjectKey);

  Optional<Image> findByTmpObjectKeyForUpdate(String tmpObjectKey);

  /**
   * Finds all images belonging to a specific reference entity. {@code referenceTypes} should be the
   * result of {@link ImageReferenceType#expand()} so that virtual types (e.g. MARKET_CLASS) are
   * automatically resolved to their concrete DB-stored subtypes (e.g. MARKET_CLASS_THUMB,
   * MARKET_CLASS_DETAIL).
   */
  List<Image> findImagesByReference(List<ImageReferenceType> referenceTypes, Long referenceId);

  /**
   * Same as {@link #findImagesByReference} but acquires a PESSIMISTIC_WRITE lock on every returned
   * row.
   */
  List<Image> findImagesByReferenceForUpdate(
      List<ImageReferenceType> referenceTypes, Long referenceId);

  /**
   * Finds all images belonging to multiple reference entities of the same reference-type family.
   * {@code referenceTypes} should already be expanded so virtual types do not reach persistence.
   */
  List<Image> findImagesByReferenceIds(
      List<ImageReferenceType> referenceTypes, List<Long> referenceIds);

  /** Finds images by their IDs. Used to validate and load the final image set on post update. */
  List<Image> findImagesByIdIn(List<Long> ids);

  /**
   * Finds images by their IDs with a pessimistic write lock. Used inside a transaction to prevent
   * concurrent modification during order update.
   */
  List<Image> findImagesByIdInForUpdate(List<Long> ids);

  /**
   * Finds non-PENDING images with {@code referenceId = null} whose {@code updated_at} is before the
   * given cutoff, up to {@code batchSize} rows ordered by id ascending. Used exclusively by {@code
   * ImageUnlinkedCleanupService}.
   *
   * <p>PENDING images whose {@code referenceId} was cleared remain in PENDING status and are
   * handled by {@code ImagePendingCleanupService}, not by this query.
   *
   * @param cutoff only images updated before this instant are eligible
   * @param batchSize maximum number of rows to return
   * @return batch of non-PENDING unlinked images ready for permanent removal
   */
  List<Image> findUnlinkedImagesBefore(Instant cutoff, int batchSize);

  /** Finds images still linked to COMMUNITY_ANSWER rows whose answer no longer exists. */
  List<Image> findOrphanAnswerImages(int batchSize);

  /** Finds images still linked to post rows whose post no longer exists. */
  List<Image> findOrphanPostImages(int batchSize);
}
