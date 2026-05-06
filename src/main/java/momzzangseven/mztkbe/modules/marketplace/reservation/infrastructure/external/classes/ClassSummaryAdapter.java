package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.classes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that resolves class summary data for the reservation module.
 *
 * <p>This is the only class in the {@code reservation} module allowed to import from the {@code
 * classes} module. It calls {@code classes} input ports exclusively — never output ports or
 * persistence layer classes.
 *
 * <p>Resolution strategy:
 *
 * <ol>
 *   <li>Use {@link GetClassInfoUseCase#findBySlotId} to resolve slotId → {@link
 *       momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass} (lock-free).
 *   <li>Use {@link GetClassDetailUseCase} with the obtained classId to fetch title, priceAmount,
 *       and thumbnailFinalObjectKey in a single input-port call.
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassSummaryAdapter implements LoadClassSummaryPort {

  private final GetClassInfoUseCase getClassInfoUseCase;
  private final GetClassDetailUseCase getClassDetailUseCase;

  @Override
  public Optional<ClassSummary> findBySlotId(Long slotId) {
    return getClassInfoUseCase
        .findBySlotId(slotId)
        .map(cls -> getClassDetailUseCase.execute(new GetClassDetailQuery(cls.getId())))
        .map(this::toClassSummary);
  }

  /**
   * Batch-load class summaries for multiple slot IDs.
   *
   * <p><b>Note:</b> currently implemented as a per-slot loop (each slot triggers up to two input
   * port calls). Suitable for typical reservation list sizes. If performance becomes a concern,
   * add a bulk {@code findBySlotIds} method to {@code GetClassInfoUseCase}.
   */
  @Override
  public Map<Long, ClassSummary> findBySlotIds(List<Long> slotIds) {
    Map<Long, ClassSummary> result = new HashMap<>();
    for (Long slotId : slotIds) {
      findBySlotId(slotId)
          .ifPresentOrElse(
              summary -> result.put(slotId, summary),
              () -> log.debug("No class summary found for slotId={}", slotId));
    }
    return result;
  }

  private ClassSummary toClassSummary(GetClassDetailResult detail) {
    return new ClassSummary(detail.title(), detail.priceAmount(), detail.thumbnailFinalObjectKey());
  }
}
