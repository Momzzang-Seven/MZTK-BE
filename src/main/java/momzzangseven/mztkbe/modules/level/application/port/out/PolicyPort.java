package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;

/** Outbound port for loading policies. */
public interface PolicyPort {
  Optional<LevelPolicy> loadLevelPolicy(int currentLevel, LocalDateTime at);

  List<LevelPolicy> loadLevelPolicies(LocalDateTime at);

  Optional<XpPolicy> loadXpPolicy(XpType type, LocalDateTime at);

  List<XpPolicy> loadXpPolicies(LocalDateTime at);
}
