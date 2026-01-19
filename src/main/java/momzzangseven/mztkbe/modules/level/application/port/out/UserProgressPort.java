package momzzangseven.mztkbe.modules.level.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;

/** Outbound port for user progress persistence. */
public interface UserProgressPort {
  Optional<UserProgress> loadUserProgress(Long userId);

  UserProgress loadOrCreateUserProgress(Long userId);

  /** Loads the user progress with a pessimistic lock to ensure data consistency during updates. */
  UserProgress loadUserProgressWithLock(Long userId);

  UserProgress saveUserProgress(UserProgress progress);
}
