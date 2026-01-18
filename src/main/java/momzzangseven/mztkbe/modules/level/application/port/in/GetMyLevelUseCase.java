package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.MyLevelResult;

public interface GetMyLevelUseCase {
  MyLevelResult execute(Long userId);
}
