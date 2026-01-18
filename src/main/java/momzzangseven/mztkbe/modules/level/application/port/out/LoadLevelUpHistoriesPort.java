package momzzangseven.mztkbe.modules.level.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;

public interface LoadLevelUpHistoriesPort {
  /**
   * Loads level-up histories ordered by createdAt desc.
   *
   * <p>Pagination is performed by {@code page}/{@code size}. Implementations may internally fetch
   * {@code size + 1} items to support {@code hasNext} computation by the application service.
   */
  List<LevelUpHistory> loadLevelUpHistories(Long userId, int page, int size);
}
