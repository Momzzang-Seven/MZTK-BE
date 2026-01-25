package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.GetWeeklyAttendanceResult;

public interface GetWeeklyAttendanceUseCase {
  GetWeeklyAttendanceResult execute(Long userId);
}
