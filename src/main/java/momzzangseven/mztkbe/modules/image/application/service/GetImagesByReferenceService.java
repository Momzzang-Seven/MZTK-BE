package momzzangseven.mztkbe.modules.image.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
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
 *   <li>Validate the ownership
 *   <li>Return the image final object keys
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetImagesByReferenceService implements GetImagesByReferenceUseCase {

  private final LoadImagePort loadImagePort;

  @Override
  public List<String> execute(GetImagesByReferenceCommand command) {

    command.validate();

    List<Image> images =
        loadImagePort.findImagesByReference(command.referenceType(), command.referenceId());

    return getFinalObjectKeyFromImages(images);
  }

  /**
   * Get the final object keys from the images.
   *
   * @param images images to get the final object keys from
   * @return List of image final object keys
   */
  private List<String> getFinalObjectKeyFromImages(List<Image> images) {
    return images.stream()
        .filter(img -> img.getStatus() == ImageStatus.COMPLETED)
        .map(Image::getFinalObjectKey)
        .toList();
  }
}
