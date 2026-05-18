package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassReservationProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSlotReservationProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSummaryProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassSlotInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes class and slot read operations to other modules via input ports.
 *
 * <p>This service exists solely as the cross-module API surface for the {@code classes} submodule.
 * Callers in other submodules (e.g., {@code reservation}) must depend on the use case interfaces
 * ({@link GetClassInfoUseCase}, {@link GetClassSlotInfoUseCase}), never on the output ports ({@code
 * LoadClassPort}, {@code LoadClassSlotPort}) directly.
 */
@Service
@RequiredArgsConstructor
public class ClassQueryFacadeService implements GetClassInfoUseCase, GetClassSlotInfoUseCase {

  private final LoadClassPort loadClassPort;
  private final LoadClassSlotPort loadClassSlotPort;
  private final LoadClassImagesPort loadClassImagesPort;

  @Override
  @Transactional(readOnly = true)
  public Optional<MarketplaceClass> findById(Long classId) {
    return loadClassPort.findById(classId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClassReservationProjection> findReservationProjectionById(Long classId) {
    return loadClassPort.findById(classId).map(this::toReservationProjection);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates to {@link LoadClassPort#findSummaryProjectionsBySlotIds} with a single-element
   * list. This avoids the 2-hop query path (slot lookup → class lookup) and reuses the same JOIN
   * query used for batch enrichment — one DB round-trip regardless of call pattern.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<ClassSummaryProjection> findBySlotId(Long slotId) {
    return Optional.ofNullable(
        loadClassPort.findSummaryProjectionsBySlotIds(List.of(slotId)).get(slotId));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates to {@link LoadClassPort#findSummaryProjectionsBySlotIds}, which issues a single
   * JPQL JOIN query. No full aggregate is loaded; only the five projection fields are returned.
   */
  @Override
  @Transactional(readOnly = true)
  public Map<Long, ClassSummaryProjection> findSummariesBySlotIds(List<Long> slotIds) {
    return loadClassPort.findSummaryProjectionsBySlotIds(slotIds);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Resolves classIds from the same projection query used by {@link #findSummariesBySlotIds},
   * then delegates to {@link LoadClassImagesPort#loadThumbnailKeys} for a single batch image
   * lookup. The result is re-keyed from classId back to slotId so callers never need to handle the
   * classId indirection.
   */
  @Override
  @Transactional(readOnly = true)
  public Map<Long, String> loadThumbnailKeysBySlotIds(List<Long> slotIds) {
    if (slotIds == null || slotIds.isEmpty()) {
      return Map.of();
    }
    // Step 1: slotId → classId (reuse the same JOIN projection, no extra query)
    Map<Long, ClassSummaryProjection> projections =
        loadClassPort.findSummaryProjectionsBySlotIds(slotIds);
    if (projections.isEmpty()) {
      return Map.of();
    }

    // Step 2: build slotId → classId lookup and collect distinct classIds
    Map<Long, Long> slotToClass =
        projections.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().classId()));
    return loadThumbnailKeysBySlotToClassMap(slotToClass);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Skips the {@code class_slots JOIN marketplace_classes} query entirely — the caller already
   * supplies the {@code slotId → classId} mapping. Only the image lookup is executed.
   */
  @Override
  @Transactional(readOnly = true)
  public Map<Long, String> loadThumbnailKeysBySlotToClassMap(Map<Long, Long> slotToClassId) {
    if (slotToClassId == null || slotToClassId.isEmpty()) {
      return Map.of();
    }
    List<Long> classIds = slotToClassId.values().stream().distinct().toList();
    Map<Long, String> thumbnailByClass = loadClassImagesPort.loadThumbnailKeys(classIds);
    return slotToClassId.entrySet().stream()
        .filter(e -> thumbnailByClass.get(e.getValue()) != null)
        .collect(Collectors.toMap(Map.Entry::getKey, e -> thumbnailByClass.get(e.getValue())));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClassSlot> findByIdWithLock(Long slotId) {
    return loadClassSlotPort.findByIdWithLock(slotId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClassSlotReservationProjection> findReservationProjectionByIdWithLock(
      Long slotId) {
    return loadClassSlotPort.findByIdWithLock(slotId).map(this::toReservationSlotProjection);
  }

  private ClassReservationProjection toReservationProjection(MarketplaceClass cls) {
    return new ClassReservationProjection(
        cls.getId(),
        cls.getTrainerId(),
        cls.getPriceAmount(),
        cls.getDurationMinutes(),
        cls.getTitle(),
        cls.isActive());
  }

  private ClassSlotReservationProjection toReservationSlotProjection(ClassSlot slot) {
    return new ClassSlotReservationProjection(
        slot.getId(),
        slot.getClassId(),
        slot.getDaysOfWeek(),
        slot.getStartTime(),
        slot.getCapacity(),
        slot.isActive());
  }
}
