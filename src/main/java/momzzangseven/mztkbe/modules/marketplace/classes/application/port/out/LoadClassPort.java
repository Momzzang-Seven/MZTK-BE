package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassItem;
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

  Optional<MarketplaceClass> findByIdForUpdate(Long classId);

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
}
