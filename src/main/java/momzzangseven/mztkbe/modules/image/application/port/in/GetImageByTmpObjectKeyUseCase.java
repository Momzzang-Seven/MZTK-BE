package momzzangseven.mztkbe.modules.image.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.image.application.dto.GetImageByTmpObjectKeyResult;

public interface GetImageByTmpObjectKeyUseCase {
  Optional<GetImageByTmpObjectKeyResult> execute(String tmpObjectKey);

  Optional<GetImageByTmpObjectKeyResult> executeForUpdate(String tmpObjectKey);
}
