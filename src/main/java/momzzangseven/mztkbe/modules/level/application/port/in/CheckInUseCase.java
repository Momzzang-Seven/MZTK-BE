package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;

public interface CheckInUseCase {
  CheckInResult execute(Long userId);
}
