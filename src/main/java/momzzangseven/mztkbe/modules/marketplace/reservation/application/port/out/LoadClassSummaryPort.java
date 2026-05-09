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
   * <p>{@code priceAmount} must be non-negative. Zero is accepted here as a "free class" value for
   * reservation enrichment purposes. A negative value indicates a data-integrity error and throws
   * {@link IllegalStateException} at construction time to catch corrupt data early at the adapter
   * boundary.
   *
   * <p><b>Policy note:</b> {@link
   * momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass} currently
   * enforces {@code priceAmount > 0} (no free classes). If the product ever introduces free classes
   * the domain invariant, DTO validation, and the {@code price_amount > 0} DB constraint must be
   * relaxed in concert. Until then, {@code priceAmount == 0} should not appear in production data
   * and would indicate a bug in the class creation flow, not a valid free-class scenario.
   *
   * @param title class title
   * @param priceAmount class price in KRW; must be &gt;= 0
   * @param thumbnailFinalObjectKey S3 object key for the thumbnail; {@code null} if not set
   */
  record ClassSummary(String title, int priceAmount, String thumbnailFinalObjectKey) {
    public ClassSummary {
      if (title == null || title.isBlank()) {
        throw new IllegalStateException("ClassSummary title must not be blank");
      }
      if (priceAmount < 0) {
        throw new IllegalStateException(
            "ClassSummary priceAmount must be >= 0, got: " + priceAmount);
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
