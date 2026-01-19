package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

public interface LevelRetentionPort {
  void deleteUserLevelDataByUserIds(List<Long> userIds);

  int deleteXpLedgerBefore(LocalDateTime cutoff, int batchSize);

  int deleteLevelUpHistoriesBefore(LocalDateTime cutoff, int batchSize);
}

