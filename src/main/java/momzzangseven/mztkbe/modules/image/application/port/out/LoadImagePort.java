package momzzangseven.mztkbe.modules.image.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.image.domain.model.Image;

public interface LoadImagePort {
  Optional<Image> findByTmpObjectKey(String tmpObjectKey);

  Optional<Image> findByTmpObjectKeyForUpdate(String tmpObjectKey);
}
