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
   * Finds unlinked images (reference_type IS NULL AND reference_id IS NULL) created before the
   * given cutoff, up to batchSize rows, ordered by id ascending. Used exclusively by {@code
   * ImageUnlinkedCleanupService}.
   *
   * @param cutoff only images created before this instant are eligible
   * @param batchSize maximum number of rows to return
   * @return batch of unlinked images ready for permanent removal
   */
  List<Image> findUnlinkedImagesBefore(Instant cutoff, int batchSize);

  /** Finds images still linked to COMMUNITY_ANSWER rows whose answer no longer exists. */
  List<Image> findOrphanAnswerImages(int batchSize);
}
