package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;

public interface GetMyLevelUpHistoriesUseCase {
  GetMyLevelUpHistoriesResult execute(Long userId, int page, int size);
}
