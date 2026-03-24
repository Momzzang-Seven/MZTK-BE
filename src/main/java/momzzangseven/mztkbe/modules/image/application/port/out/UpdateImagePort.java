package momzzangseven.mztkbe.modules.image.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.model.Image;

/** Hexagonal Architecture: OUTPUT PORT. Abstraction for updating an existing image record. */
public interface UpdateImagePort {

  /**
   * Persists the updated image domain object.
   *
   * @param image updated domain object
   * @return persisted domain object
   */
  Image update(Image image);

  /**
   * Bulk-persists updated image domain objects in a single operation.
   *
   * @param images list of updated domain objects
   * @return list of persisted domain objects
   */
  List<Image> updateAll(List<Image> images);
}
