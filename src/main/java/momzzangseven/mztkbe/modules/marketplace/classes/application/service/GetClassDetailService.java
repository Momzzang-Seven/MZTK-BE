package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSlotInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailResult.StoreInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassImagesPort.ClassImages;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for fetching the full detail of a single marketplace class.
 *
 * <p>The persistence adapter provides a projection that includes joined store data (<b>store info
 * is loaded via JPQL</b> in {@link LoadClassPort#findClassDetailById}). Active class time slots are
 * loaded separately via {@link LoadClassSlotPort} and merged into the result.
 *
 * <p>Images (THUMB + DETAIL) are loaded separately via the image module port.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetClassDetailService implements GetClassDetailUseCase {

  private final LoadClassPort loadClassPort;
  private final LoadClassImagesPort loadClassImagesPort;
  private final LoadClassSlotPort loadClassSlotPort;

  @Override
  public GetClassDetailResult execute(GetClassDetailQuery query) {
    log.debug("Fetching class detail: classId={}", query.classId());

    // Load class detail (with store info via JPQL join)
    ClassDetailInfo detail =
        loadClassPort
            .findClassDetailById(query.classId())
            .orElseThrow(() -> new ClassNotFoundException(query.classId()));

    // Load active time-slots (read-only, no lock needed for display)
    List<ClassSlotInfo> classTimes =
        loadClassSlotPort.findByClassId(query.classId()).stream()
            .filter(ClassSlot::isActive)
            .map(
                slot ->
                    new ClassSlotInfo(
                        slot.getId(),
                        slot.getDaysOfWeek(),
                        slot.getStartTime(),
                        slot.getCapacity()))
            .toList();

    ClassImages classImages = loadClassImagesPort.loadImages(query.classId());

    StoreInfo storeInfo =
        new StoreInfo(
            detail.storeId(),
            detail.storeName(),
            detail.storeAddress(),
            detail.storeDetailAddress(),
            detail.storeLatitude(),
            detail.storeLongitude());

    return new GetClassDetailResult(
        detail.classId(),
        detail.trainerId(),
        storeInfo,
        detail.title(),
        detail.category(),
        detail.description(),
        detail.priceAmount(),
        classImages.thumbnailFinalObjectKey(),
        classImages.detailImages(),
        detail.tags(),
        detail.features(),
        detail.durationMinutes(),
        detail.personalItems(),
        classTimes);
  }
}
