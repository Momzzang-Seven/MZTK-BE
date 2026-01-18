package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** Domain model for level-up requirement/reward policy for a given current level. */
@Getter
@Builder(toBuilder = true)
public class LevelPolicy {
  private Long id;
  private int level;
  private int requiredXp;
  private int rewardMztk;
  private LocalDateTime effectiveFrom;
  private LocalDateTime effectiveTo;
  private boolean enabled;
}
