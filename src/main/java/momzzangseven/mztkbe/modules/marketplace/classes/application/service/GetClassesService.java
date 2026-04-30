package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.classes.application.MarketplacePaginationConstants;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassesResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassesUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassTagPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for retrieving a paginated list of active marketplace classes.
 *
 * <p>After fetching the page of class projections from persistence, two batch lookups are
 * performed:
 *
 * <ol>
 *   <li>Tag names — via {@link LoadClassTagPort#findTagsByClassIdsIn} (one IN query)
 *   <li>Thumbnail keys — via {@link LoadClassImagesPort#loadThumbnailKeys} (one IN query)
 * </ol>
 *
 * <p>Both are merged into the final {@link ClassItem} list to avoid N+1 queries.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetClassesService implements GetClassesUseCase {

  private static final String SORT_DISTANCE = "DISTANCE";

  private final LoadClassPort loadClassPort;
  private final LoadClassImagesPort loadClassImagesPort;
  private final LoadClassTagPort loadClassTagPort;

  @Override
  public GetClassesResult execute(GetClassesQuery query) {
    log.debug("Fetching class list: page={}", query.page());

    String effectiveSort = resolveSort(query);

    PageRequest pageable =
        PageRequest.of(query.page(), MarketplacePaginationConstants.DEFAULT_PAGE_SIZE);
    Page<ClassItem> page =
        loadClassPort.findActiveClasses(
            query.lat(),
            query.lng(),
            query.category() != null ? query.category().name() : null,
            effectiveSort,
            query.trainerId(),
            query.startTime(),
            query.endTime(),
            pageable);

    List<Long> classIds = page.getContent().stream().map(ClassItem::classId).toList();

    if (classIds.isEmpty()) {
      return GetClassesResult.of(
          List.of(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }

    // Batch-load tags — one IN query for the whole page
    Map<Long, List<String>> tagMap = loadClassTagPort.findTagsByClassIdsIn(classIds);

    // Batch-load thumbnails — one IN query via image module
    Map<Long, String> thumbnailKeys = loadClassImagesPort.loadThumbnailKeys(classIds);

    List<ClassItem> enriched =
        page.getContent().stream()
            .map(
                item ->
                    new ClassItem(
                        item.classId(),
                        item.title(),
                        item.category(),
                        item.priceAmount(),
                        item.durationMinutes(),
                        thumbnailKeys.getOrDefault(item.classId(), null),
                        tagMap.getOrDefault(item.classId(), List.of()),
                        item.distance()))
            .toList();

    return GetClassesResult.of(
        enriched, page.getNumber(), page.getTotalPages(), page.getTotalElements());
  }

  /**
   * Resolves the effective sort key to pass to the persistence layer.
   *
   * <ul>
   *   <li>sort=DISTANCE + lat/lng absent → RATING (spec §4: "위치 권한 미설정 시 RATING 자동 폴백")
   *   <li>sort=null/blank + lat/lng present → DISTANCE (spec §4: 위치정보 있을 때 기본 DISTANCE)
   *   <li>sort=null/blank + lat/lng absent → RATING
   *   <li>Any other explicit sort value → passed through as-is; the adapter's switch default
   *       handles unknown values by falling back to LATEST.
   * </ul>
   */
  private String resolveSort(GetClassesQuery query) {
    boolean hasLocation = query.lat() != null && query.lng() != null;

    // Explicit DISTANCE request without location → downgrade to default sort
    if (SORT_DISTANCE.equalsIgnoreCase(query.sort()) && !hasLocation) {
      return MarketplacePaginationConstants.DEFAULT_SORT;
    }

    // No sort specified → use DISTANCE when location is available, default sort otherwise
    if (query.sort() == null || query.sort().isBlank()) {
      return hasLocation ? SORT_DISTANCE : MarketplacePaginationConstants.DEFAULT_SORT;
    }

    return query.sort();
  }
}
