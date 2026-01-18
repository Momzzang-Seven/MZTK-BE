package momzzangseven.mztkbe.modules.level.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;

public interface LoadUserProgressPort {
  Optional<UserProgress> loadUserProgress(Long userId);

  UserProgress loadOrCreateUserProgress(Long userId);

  /** Loads the user progress with a pessimistic lock to ensure data consistency during updates. */
  UserProgress loadUserProgressWithLock(Long userId);
}
