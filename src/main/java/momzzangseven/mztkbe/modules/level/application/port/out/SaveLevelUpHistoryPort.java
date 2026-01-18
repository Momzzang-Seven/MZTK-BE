package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;

public interface SaveLevelUpHistoryPort {
  LevelUpHistory saveLevelUpHistory(LevelUpHistory history);
}
