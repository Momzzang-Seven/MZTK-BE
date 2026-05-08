package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase.ClassSummaryProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Output port for loading marketplace class data from persistence.
 *
 * <p>Hexagonal Architecture: defined by the application layer and implemented by the infrastructure
 * adapter.
 */
public interface LoadClassPort {

  /**
   * Find a class by its ID.
   *
   * @param classId class ID
   * @return Optional containing the domain model if found
   */
  Optional<MarketplaceClass> findById(Long classId);

  /**
   * Find all active classes matching the given filters, sorted and paginated.
   *
   * <p>Distance sort requires non-null lat/lng; when unavailable the persistence layer falls back
   * to rating sort.
   *
   * @param lat user latitude (nullable)
   * @param lng user longitude (nullable)
   * @param category category filter (nullable)
   * @param sort sort hint (nullable, defaults applied in adapter)
   * @param trainerId trainer filter (nullable)
   * @param startTime time range lower bound (nullable)
   * @param endTime time range upper bound (nullable)
   * @param pageable pagination
   * @return paged list of ClassItem projections
   */
  Page<ClassItem> findActiveClasses(
      Double lat,
      Double lng,
      String category,
      String sort,
      Long trainerId,
      String startTime,
      String endTime,
      Pageable pageable);

  /**
   * Load the full detail projection for a single class (including joined store data).
   *
   * @param classId class ID
   * @return Optional containing the detail info if found
   */
  Optional<ClassDetailInfo> findClassDetailById(Long classId);

  /**
   * Find all classes belonging to a trainer (including inactive), paginated.
   *
   * @param trainerId trainer ID
   * @param pageable pagination
   * @return paged list of MarketplaceClass domain models
   */
  Page<MarketplaceClass> findByTrainerId(Long trainerId, Pageable pageable);

  /**
   * Bulk-load lightweight class projections keyed by slot ID.
   *
   * <p>A single query joins {@code class_slots} → {@code marketplace_classes} and projects only
   * {@code classId, trainerId, title, priceAmount, active}. No tags, features, store, or image data
   * is loaded. Used by the reservation-enrichment adapter to avoid N+1 calls and the overhead of
   * loading the full class aggregate.
   *
   * @param slotIds list of slot IDs
   * @return map of slotId → {@link ClassSummaryProjection}; absent means no class found for that
   *     slot
   */
  Map<Long, ClassSummaryProjection> findSummaryProjectionsBySlotIds(List<Long> slotIds);
}
