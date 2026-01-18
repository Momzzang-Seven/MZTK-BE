package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

public interface LoadXpPolicyPort {
  Optional<XpPolicy> loadXpPolicy(XpType type, LocalDateTime at);
}
