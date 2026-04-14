package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;

/** Input port for getting image list linked to specific reference. */
public interface GetImagesByReferenceUseCase {
  /**
   * Get images by reference.
   *
   * @param command GetImagesByReferenceCommand
   * @return result wrapping a list of (imageId, finalObjectKey) for all linked images, ordered by
   *     img_order; {@code finalObjectKey} is {@code null} for PENDING/FAILED images
   */
  GetImagesByReferenceResult execute(GetImagesByReferenceCommand command);
}
