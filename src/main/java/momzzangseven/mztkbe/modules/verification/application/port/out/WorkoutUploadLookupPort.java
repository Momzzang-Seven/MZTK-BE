package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;

public interface WorkoutUploadLookupPort {
  Optional<WorkoutUploadReference> findByTmpObjectKey(String tmpObjectKey);

  Optional<WorkoutUploadReference> findByTmpObjectKeyForUpdate(String tmpObjectKey);
}
