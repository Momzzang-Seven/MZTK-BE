package momzzangseven.mztkbe.modules.user.infrastructure.external.level.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GetAttendanceStatusResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetWeeklyAttendanceResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetAttendanceStatusUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetWeeklyAttendanceUseCase;
import momzzangseven.mztkbe.modules.user.application.dto.AttendanceSummary;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadAttendanceSummaryPort;
import org.springframework.stereotype.Component;

/**
 * Driven adapter that bridges the user module's {@link LoadAttendanceSummaryPort} to the level
 * module's attendance use cases. Calls both {@link GetAttendanceStatusUseCase} (today's attendance)
 * and {@link GetWeeklyAttendanceUseCase} (weekly count), then merges results into {@link
 * AttendanceSummary}.
 */
@Component
@RequiredArgsConstructor
public class AttendanceSummaryAdapter implements LoadAttendanceSummaryPort {

  private final GetAttendanceStatusUseCase getAttendanceStatusUseCase;
  private final GetWeeklyAttendanceUseCase getWeeklyAttendanceUseCase;

  /**
   * Loads today's attendance status and weekly attendance count for the given user.
   *
   * @param userId the user's ID
   * @return combined attendance summary
   */
  @Override
  public AttendanceSummary loadSummary(Long userId) {
    GetAttendanceStatusResult statusResult = getAttendanceStatusUseCase.execute(userId);
    GetWeeklyAttendanceResult weeklyResult = getWeeklyAttendanceUseCase.execute(userId);
    return new AttendanceSummary(statusResult.hasAttendedToday(), weeklyResult.attendedCount());
  }
}
