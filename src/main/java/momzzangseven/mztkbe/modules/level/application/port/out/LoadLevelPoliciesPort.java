package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;

public interface LoadLevelPoliciesPort {
  List<LevelPolicy> loadLevelPolicies(LocalDateTime at);
}
