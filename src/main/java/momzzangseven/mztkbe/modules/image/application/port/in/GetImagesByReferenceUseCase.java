package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;

/** Input port for getting image list linked to specific reference. */
public interface GetImagesByReferenceUseCase {
  /**
   * Get images by reference.
   *
   * @param command GetImagesByReferenceCommand
   * @return result wrapping a list of (imageId, finalObjectKey) for COMPLETED images, ordered by
   *     img_order
   */
  GetImagesByReferenceResult execute(GetImagesByReferenceCommand command);
}
