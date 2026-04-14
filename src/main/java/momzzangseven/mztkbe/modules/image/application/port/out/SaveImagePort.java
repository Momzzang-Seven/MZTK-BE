package momzzangseven.mztkbe.modules.image.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.model.Image;

/**
 * Hexagonal Architecture: OUTPUT PORT. Abstraction for persisting new ImageEntity records to the
 * database.
 */
public interface SaveImagePort {

  /** Bulk-inserts a list of ImageEntity domain objects and returns persisted instances with IDs. */
  List<Image> saveAll(List<Image> images);
}
