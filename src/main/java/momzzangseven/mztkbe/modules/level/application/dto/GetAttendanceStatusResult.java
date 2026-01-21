package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDate;

public record GetAttendanceStatusResult(
    LocalDate today, boolean hasAttendedToday, int streakCount) {
  public static GetAttendanceStatusResult of(
      LocalDate today, boolean hasAttendedToday, int streakCount) {
    return new GetAttendanceStatusResult(today, hasAttendedToday, streakCount);
  }
}
