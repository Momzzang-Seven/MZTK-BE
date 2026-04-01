package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;

/** Command-side port that guarantees a progress row exists before mutation flows proceed. */
public interface EnsureUserProgressPort {
  UserProgress loadOrCreateUserProgress(Long userId);
}
