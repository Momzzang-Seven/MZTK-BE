package momzzangseven.mztkbe.modules.marketplace.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.MarketplacePaginationConstants;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesResult.TrainerClassItem;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetTrainerClassesUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for retrieving the authenticated trainer's own class list.
 *
 * <p>Returns both active and inactive classes. Thumbnail keys are batch-loaded from the image
 * module.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetTrainerClassesService implements GetTrainerClassesUseCase {

  private final LoadClassPort loadClassPort;
  private final LoadClassImagesPort loadClassImagesPort;

  @Override
  public GetTrainerClassesResult execute(GetTrainerClassesQuery query) {
    log.debug("Fetching trainer classes: trainerId={}, page={}", query.trainerId(), query.page());

    PageRequest pageable =
        PageRequest.of(query.page(), MarketplacePaginationConstants.DEFAULT_PAGE_SIZE);
    Page<MarketplaceClass> page = loadClassPort.findByTrainerId(query.trainerId(), pageable);

    List<Long> classIds = page.getContent().stream().map(MarketplaceClass::getId).toList();
    Map<Long, String> thumbnailKeys =
        classIds.isEmpty() ? Map.of() : loadClassImagesPort.loadThumbnailKeys(classIds);

    List<TrainerClassItem> items =
        page.getContent().stream()
            .map(
                c ->
                    new TrainerClassItem(
                        c.getId(),
                        c.getTitle(),
                        c.getCategory() != null ? c.getCategory().name() : null,
                        c.getPriceAmount(),
                        c.getTags(),
                        c.isActive(),
                        thumbnailKeys.get(c.getId())))
            .toList();

    return GetTrainerClassesResult.of(
        items, page.getNumber(), page.getTotalPages(), page.getTotalElements());
  }
}
