package momzzangseven.mztkbe.modules.image.application.port.out;

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
}
