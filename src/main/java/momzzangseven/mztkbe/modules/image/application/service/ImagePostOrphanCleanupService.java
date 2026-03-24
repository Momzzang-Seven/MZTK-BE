package momzzangseven.mztkbe.modules.image.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.port.in.RunOrphanPostImageCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.infrastructure.config.ImagePostOrphanCleanupProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles images still linked to deleted post rows after post-commit unlink failures. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagePostOrphanCleanupService implements RunOrphanPostImageCleanupBatchUseCase {

  private final LoadImagePort loadImagePort;
  private final DeleteImagePort deleteImagePort;
  private final ImagePostOrphanCleanupProperties props;

  @Override
  @Transactional
  public int runBatch() {
    List<Image> orphanImages = loadImagePort.findOrphanPostImages(props.getBatchSize());
    if (orphanImages.isEmpty()) {
      return 0;
    }

    List<Long> imageIds = orphanImages.stream().map(Image::getId).toList();
    deleteImagePort.unlinkImagesByIdIn(imageIds);

    log.info("Orphan post image cleanup batch: unlinked={}", imageIds.size());
    return imageIds.size();
  }
}
