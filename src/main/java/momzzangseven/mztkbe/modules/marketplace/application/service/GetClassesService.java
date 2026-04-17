package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.MarketplacePaginationConstants;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassesResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetClassesUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for retrieving a paginated list of active marketplace classes.
 *
 * <p>After fetching the page of class projections, thumbnail keys are loaded in batch (one IN
 * query) to avoid N+1 queries.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetClassesService implements GetClassesUseCase {

  private static final String SORT_DISTANCE = "DISTANCE";
  private static final String SORT_RATING = "RATING";


  private final LoadClassPort loadClassPort;
  private final LoadClassImagesPort loadClassImagesPort;

  @Override
  public GetClassesResult execute(GetClassesQuery query) {
    log.debug("Fetching class list: page={}", query.page());

    // Fall back to RATING when location data is missing and DISTANCE is requested
    String effectiveSort = resolveSort(query);

    PageRequest pageable = PageRequest.of(query.page(), MarketplacePaginationConstants.DEFAULT_PAGE_SIZE);
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

    // Batch load thumbnails
    List<Long> classIds = page.getContent().stream().map(ClassItem::classId).toList();
    Map<Long, String> thumbnailKeys =
        classIds.isEmpty() ? Map.of() : loadClassImagesPort.loadThumbnailKeys(classIds);

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
                        item.tags(),
                        item.distance()))
            .toList();

    return GetClassesResult.of(
        enriched, page.getNumber(), page.getTotalPages(), page.getTotalElements());
  }

  private String resolveSort(GetClassesQuery query) {
    if (SORT_DISTANCE.equalsIgnoreCase(query.sort())
        && (query.lat() == null || query.lng() == null)) {
      return SORT_RATING;
    }
    return (query.sort() != null && !query.sort().isBlank()) ? query.sort() : SORT_RATING;
  }
}
