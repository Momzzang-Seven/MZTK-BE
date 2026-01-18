package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.MyLevelUpHistoriesResult;

public interface GetMyLevelUpHistoriesUseCase {
  MyLevelUpHistoriesResult execute(Long userId, int page, int size);
}
