package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;

public interface LoadLevelPolicyPort {
  Optional<LevelPolicy> loadLevelPolicy(int currentLevel, LocalDateTime at);
}
