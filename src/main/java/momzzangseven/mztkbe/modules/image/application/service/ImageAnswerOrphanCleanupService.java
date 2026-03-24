package momzzangseven.mztkbe.modules.image.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.image.application.port.in.RunOrphanAnswerImageCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.infrastructure.config.ImageAnswerOrphanCleanupProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles images still linked to COMMUNITY_ANSWER rows whose answer has already been deleted.
 *
 * <p>This compensates for failures in the post-commit answer image unlink handler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnswerOrphanCleanupService implements RunOrphanAnswerImageCleanupBatchUseCase {

  private final LoadImagePort loadImagePort;
  private final DeleteImagePort deleteImagePort;
  private final ImageAnswerOrphanCleanupProperties props;

  @Override
  @Transactional
  public int runBatch() {
    List<Image> orphanImages = loadImagePort.findOrphanAnswerImages(props.getBatchSize());
    if (orphanImages.isEmpty()) {
      return 0;
    }

    List<Long> imageIds = orphanImages.stream().map(Image::getId).toList();
    deleteImagePort.unlinkImagesByIdIn(imageIds);

    log.info("Orphan answer image cleanup batch: unlinked={}", imageIds.size());
    return imageIds.size();
  }
}
