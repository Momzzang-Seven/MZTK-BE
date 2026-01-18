package momzzangseven.mztkbe.modules.level.application.port.out;

import momzzangseven.mztkbe.modules.level.application.port.out.dto.LevelUpHistorySlice;

public interface LoadLevelUpHistoriesPort {
  LevelUpHistorySlice loadLevelUpHistories(Long userId, int page, int size);
}
