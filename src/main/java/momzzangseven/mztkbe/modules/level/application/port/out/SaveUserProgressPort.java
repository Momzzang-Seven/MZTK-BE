package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;

public interface SaveUserProgressPort {
  UserProgress saveUserProgress(UserProgress progress);
}
