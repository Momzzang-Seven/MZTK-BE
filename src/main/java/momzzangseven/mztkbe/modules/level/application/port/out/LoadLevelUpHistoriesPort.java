package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoriesSlice;

public interface LoadLevelUpHistoriesPort {
  LevelUpHistoriesSlice loadLevelUpHistories(Long userId, int page, int size);
}
