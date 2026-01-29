package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.GetAttendanceStatusResult;

public interface GetAttendanceStatusUseCase {
  GetAttendanceStatusResult execute(Long userId);
}
