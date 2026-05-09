package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.classes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSummaryProjection;
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
 * <h2>Enrichment strategy</h2>
 *
 * <ul>
 *   <li><b>Bulk path ({@link #findBySlotIds}):</b> delegates to {@link
 *       GetClassInfoUseCase#findSummariesBySlotIds}, which issues a single JPQL JOIN query ({@code
 *       class_slots JOIN marketplace_classes}). Title, price, and active flag are projected. The
 *       resulting {@code slotId → classId} mapping is then passed to {@link
 *       GetClassInfoUseCase#loadThumbnailKeysBySlotToClassMap} so that thumbnail keys are
 *       batch-fetched without re-executing the JOIN.
 *   <li><b>Single path ({@link #findBySlotId}):</b> delegates to the bulk method with a
 *       single-element list to avoid code duplication.
 * </ul>
 *
 * <h2>Inactive-class handling</h2>
 *
 * <p>If the class is marked inactive after the reservation was created, {@link
 * ClassSummaryProjection#active()} is {@code false}. The adapter converts such projections to
 * {@link Optional#empty()} / absent map entries so that past reservations linked to inactive
 * classes still render — with enrichment fields omitted — rather than failing with HTTP 500.
 *
 * <h2>Data-integrity fallback</h2>
 *
 * <p>If the projection's title is blank or priceAmount is negative (corrupt data), constructing
 * {@link LoadClassSummaryPort.ClassSummary} throws {@link IllegalStateException}. This is caught
 * and logged as a warning so that a single bad record does not fail the entire list or detail
 * query. Note: {@code priceAmount == 0} is valid for free classes and is not treated as an error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassSummaryAdapter implements LoadClassSummaryPort {

  private final GetClassInfoUseCase getClassInfoUseCase;

  /**
   * {@inheritDoc}
   *
   * <p>Delegates to {@link #findBySlotIds} with a single-element list to reuse the same batch query
   * path and avoid code duplication.
   */
  @Override
  public Optional<ClassSummary> findBySlotId(Long slotId) {
    return Optional.ofNullable(findBySlotIds(List.of(slotId)).get(slotId));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Calls {@link GetClassInfoUseCase#findSummariesBySlotIds} which issues a single JOIN query.
   * Inactive classes and data-integrity failures are silently dropped (logged at WARN/DEBUG level).
   */
  @Override
  public Map<Long, ClassSummary> findBySlotIds(List<Long> slotIds) {
    if (slotIds == null || slotIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, ClassSummaryProjection> projections =
        getClassInfoUseCase.findSummariesBySlotIds(slotIds);

    // Build slotId → classId from the projections we already have, then pass the mapping
    // to loadThumbnailKeysBySlotToClassMap so the JOIN is not repeated for thumbnail resolution.
    Map<Long, Long> slotToClassId = new HashMap<>();
    for (Map.Entry<Long, ClassSummaryProjection> entry : projections.entrySet()) {
      if (entry.getValue().active()) {
        slotToClassId.put(entry.getKey(), entry.getValue().classId());
      }
    }
    Map<Long, String> thumbnailKeys =
        slotToClassId.isEmpty()
            ? Map.of()
            : getClassInfoUseCase.loadThumbnailKeysBySlotToClassMap(slotToClassId);

    Map<Long, ClassSummary> result = new HashMap<>();
    for (Map.Entry<Long, ClassSummaryProjection> entry : projections.entrySet()) {
      Long slotId = entry.getKey();
      ClassSummaryProjection proj = entry.getValue();

      if (!proj.active()) {
        log.debug(
            "Skipping ClassSummary for slotId={} classId={}: class is inactive",
            slotId,
            proj.classId());
        continue;
      }

      try {
        result.put(
            slotId, new ClassSummary(proj.title(), proj.priceAmount(), thumbnailKeys.get(slotId)));
      } catch (IllegalStateException e) {
        log.warn(
            "Skipping ClassSummary for slotId={} classId={} due to invariant violation: {}",
            slotId,
            proj.classId(),
            e.getMessage());
      }
    }
    return result;
  }
}
