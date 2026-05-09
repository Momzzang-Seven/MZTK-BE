package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSummaryProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;

/**
 * Input port that exposes class read operations to other modules.
 *
 * <p>Cross-module callers (e.g., the {@code reservation} module) must use this interface instead of
 * directly referencing the output port {@code LoadClassPort}. This keeps the dependency direction
 * correct: only {@code application/port/in/} is the public API surface of a module.
 *
 * <h2>Cross-module enrichment policy</h2>
 *
 * <p>Prefer {@link #findSummariesBySlotIds} for reservation-enrichment use cases. It returns a
 * lightweight {@link ClassSummaryProjection} that exposes only the fields needed for display,
 * instead of the full {@link MarketplaceClass} aggregate. This keeps the module boundary thin and
 * avoids coupling callers to internal aggregate changes.
 */
public interface GetClassInfoUseCase {

  /**
   * Batch-load class summary projections keyed by slot ID.
   *
   * <p>Preferred entry point for reservation-enrichment flows. A single JPQL query joins {@code
   * class_slots} → {@code marketplace_classes} and returns only the columns needed for enrichment —
   * no tags, features, store, or image data is loaded.
   *
   * @param slotIds list of slot IDs to resolve
   * @return map of slotId → {@link ClassSummaryProjection}; absent key means the slot or class was
   *     not found
   */
  Map<Long, ClassSummaryProjection> findSummariesBySlotIds(List<Long> slotIds);

  /**
   * Batch-load thumbnail final-object-keys keyed by slot ID.
   *
   * <p>Resolves the classId for each slot (via the same JOIN query used by {@link
   * #findSummariesBySlotIds}) and then batch-fetches thumbnail keys from the image module in a
   * single round-trip. Absent keys in the returned map mean the slot/class has no thumbnail.
   *
   * <p><b>Prefer {@link #loadThumbnailKeysBySlotToClassMap} when the caller already holds a {@code
   * slotId → classId} mapping</b> (e.g., from a preceding {@link #findSummariesBySlotIds} call) to
   * avoid the redundant JOIN round-trip.
   *
   * @param slotIds list of slot IDs
   * @return map of slotId → thumbnail finalObjectKey
   */
  Map<Long, String> loadThumbnailKeysBySlotIds(List<Long> slotIds);

  /**
   * Batch-load thumbnail final-object-keys keyed by slot ID using a caller-supplied {@code slotId →
   * classId} mapping.
   *
   * <p>Use this overload when the caller already possesses the slot-to-class mapping (e.g., from a
   * preceding {@link #findSummariesBySlotIds} call). Unlike {@link
   * #loadThumbnailKeysBySlotIds(List)}, this method does <em>not</em> re-execute the {@code
   * class_slots JOIN marketplace_classes} query, saving one DB round-trip per list request.
   *
   * @param slotToClassId map of slotId → classId (pre-resolved by the caller)
   * @return map of slotId → thumbnail finalObjectKey; absent key means no thumbnail
   */
  Map<Long, String> loadThumbnailKeysBySlotToClassMap(Map<Long, Long> slotToClassId);

  /**
   * Find a class aggregate by its ID.
   *
   * <p><b>Cross-module callers</b>: use {@link #findSummariesBySlotIds} where possible. This method
   * loads the full aggregate (including features/personalItems) and is intended for intra-module
   * use or when the full aggregate is genuinely needed (e.g., CreateReservationService validating
   * price and duration).
   *
   * @param classId class ID
   * @return Optional containing the class aggregate if found
   */
  Optional<MarketplaceClass> findById(Long classId);

  /**
   * Find a class summary projection by the given slot ID.
   *
   * <p><b>Cross-module callers:</b> this method returns a {@link ClassSummaryProjection} so that
   * the {@code reservation} module never needs to depend on the {@link
   * momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass} aggregate. Use
   * {@link #findSummariesBySlotIds} for bulk enrichment; this method is retained for single-slot
   * detail lookups (e.g., reservation detail enrichment fallback for legacy records).
   *
   * @param slotId slot ID
   * @return Optional containing the summary projection if the slot and its class are found
   */
  Optional<ClassSummaryProjection> findBySlotId(Long slotId);
}
