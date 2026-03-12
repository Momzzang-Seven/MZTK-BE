package momzzangseven.mztkbe.modules.image.application.port.out;

import java.time.Instant;

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
}
