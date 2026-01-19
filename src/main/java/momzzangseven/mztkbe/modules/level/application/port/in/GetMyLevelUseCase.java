package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelResult;

public interface GetMyLevelUseCase {
  GetMyLevelResult execute(Long userId);
}
