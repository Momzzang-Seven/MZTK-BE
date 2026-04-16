package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailResult.StoreInfo;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetClassDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassImagesPort.ClassImages;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for fetching the full detail of a single marketplace class.
 *
 * <p>The persistence adapter provides a projection that includes joined store data. Images (THUMB +
 * DETAIL) are loaded separately via the image module port.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetClassDetailService implements GetClassDetailUseCase {

  private final LoadClassPort loadClassPort;
  private final LoadClassImagesPort loadClassImagesPort;

  @Override
  public GetClassDetailResult execute(GetClassDetailQuery query) {
    log.debug("Fetching class detail: classId={}", query.classId());

    ClassDetailInfo detail =
        loadClassPort
            .findClassDetailById(query.classId())
            .orElseThrow(() -> new ClassNotFoundException(query.classId()));

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
        detail.classTimes());
  }
}
