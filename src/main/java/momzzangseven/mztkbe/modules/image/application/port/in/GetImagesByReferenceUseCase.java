package momzzangseven.mztkbe.modules.image.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;

/** Input port for getting image list linked to specific reference. */
public interface GetImagesByReferenceUseCase {
  /**
   * Get images by reference.
   *
   * @param command GetImagesByReferenceCommand
   * @return List of image final object keys
   */
  List<String> execute(GetImagesByReferenceCommand command);
}
