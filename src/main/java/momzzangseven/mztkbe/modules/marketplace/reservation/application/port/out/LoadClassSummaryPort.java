package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Output port for fetching class summary data needed to enrich reservation responses.
 *
 * <p>Implemented by {@code ClassSummaryAdapter} in the reservation module's external layer. The
 * adapter is the only place allowed to cross the module boundary into the {@code classes} module.
 */
public interface LoadClassSummaryPort {

  /**
   * Summary of a class required for reservation display.
   *
   * <p>{@code priceAmount} must be strictly positive, matching the {@code price_amount > 0} DB
   * constraint and the {@link
   * momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass} domain
   * invariant. A value of zero or below indicates a data-integrity error (e.g. a bug in the class
   * creation flow or a legacy import) and throws {@link IllegalStateException} at construction time
   * so corrupt data is caught early at the adapter boundary rather than propagated to callers.
   *
   * <p>If the product ever introduces free classes, the class creation/update DTOs, domain
   * invariant, and the {@code price_amount > 0} DB constraint must be relaxed in concert before
   * this guard is loosened.
   *
   * @param title class title; must not be blank
   * @param priceAmount class price in KRW; must be &gt; 0
   * @param thumbnailFinalObjectKey S3 object key for the thumbnail; {@code null} if not set
   */
  record ClassSummary(String title, int priceAmount, String thumbnailFinalObjectKey) {
    public ClassSummary {
      if (title == null || title.isBlank()) {
        throw new IllegalStateException("ClassSummary title must not be blank");
      }
      if (priceAmount <= 0) {
        throw new IllegalStateException(
            "ClassSummary priceAmount must be > 0, got: " + priceAmount);
      }
    }
  }

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
