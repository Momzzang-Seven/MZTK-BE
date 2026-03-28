package momzzangseven.mztkbe.modules.image.application.service;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByIdsUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that fetches image metadata for a given list of image IDs.
 *
 * <p>Execution phases:
 *
 * <ol>
 *   <li>Validate the command
 *   <li>Load images by ID list (soft-miss: non-existent IDs are silently absent)
 *   <li>Verify 3-factor ownership (userId / referenceType / referenceId) for every loaded image;
 *       any mismatch rejects the entire request with 403
 *   <li>Map domain objects to result items and return
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetImagesByIdsService implements GetImagesByIdsUseCase {

  private final LoadImagePort loadImagePort;

  @Override
  public GetImagesByIdsResult execute(GetImagesByIdsCommand command) {
    command.validate();

    List<Image> images = loadImagePort.findImagesByIdIn(command.ids());

    for (Image image : images) {
      verifyOwnership(command, image);
    }

    List<ImageItem> items =
        images.stream()
            .sorted(Comparator.comparingInt(Image::getImgOrder))
            .map(ImageItem::from)
            .toList();
    return new GetImagesByIdsResult(items);
  }

  private void verifyOwnership(GetImagesByIdsCommand command, Image image) {
    boolean ownerMatch = command.userId().equals(image.getUserId());
    boolean typeMatch = command.referenceType().expand().contains(image.getReferenceType());
    boolean refMatch = command.referenceId().equals(image.getReferenceId());

    if (!ownerMatch || !typeMatch || !refMatch) {
      throw new ImageNotBelongsToUserException("Image does not belong to user or given reference");
    }
  }
}
