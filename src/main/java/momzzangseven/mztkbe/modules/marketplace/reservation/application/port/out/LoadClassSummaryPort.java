package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Output port for fetching class summary data needed to enrich reservation responses.
 *
 * <p>Implemented by {@code ClassSummaryAdapter} in the reservation module's external layer. The
 * adapter is the only place allowed to cross the module boundary into the {@code classes} module.
 *
 * @param title class title
 * @param priceAmount class price in KRW
 * @param thumbnailFinalObjectKey S3 object key for the thumbnail; {@code null} if not set
 */
public interface LoadClassSummaryPort {

  /**
   * Summary of a class required for reservation display.
   *
   * @param title class title
   * @param priceAmount class price in KRW
   * @param thumbnailFinalObjectKey S3 object key for the thumbnail; {@code null} if not set
   */
  record ClassSummary(String title, int priceAmount, String thumbnailFinalObjectKey) {}

  /**
   * Find the class summary that owns the given slot (lock-free read).
   *
   * @param slotId slot ID
   * @return Optional containing the summary if the slot and its class are found
   */
  Optional<ClassSummary> findBySlotId(Long slotId);

  /**
   * Batch-load class summaries for multiple slot IDs. Used in list endpoints to avoid N+1 calls.
   *
   * @param slotIds list of slot IDs
   * @return map of slotId → ClassSummary; absent key means the slot or class was not found
   */
  Map<Long, ClassSummary> findBySlotIds(List<Long> slotIds);
}
