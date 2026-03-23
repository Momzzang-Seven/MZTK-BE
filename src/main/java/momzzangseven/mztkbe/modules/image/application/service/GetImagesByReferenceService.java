package momzzangseven.mztkbe.modules.image.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that gets images by reference.
 *
 * <p>Execution phases:
 *
 * <ol>
 *   <li>Validate the command
 *   <li>Load the images
 *   <li>Return COMPLETED images wrapped in {@link GetImagesByReferenceResult}
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetImagesByReferenceService implements GetImagesByReferenceUseCase {

  private final LoadImagePort loadImagePort;

  @Override
  public GetImagesByReferenceResult execute(GetImagesByReferenceCommand command) {
    command.validate();

    List<Image> images =
        loadImagePort.findImagesByReference(command.referenceType(), command.referenceId());

    List<ImageItem> items =
        images.stream()
            .filter(img -> img.getStatus() == ImageStatus.COMPLETED)
            .map(ImageItem::from)
            .toList();

    return GetImagesByReferenceResult.of(items);
  }
}
