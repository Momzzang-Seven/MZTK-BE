package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDate;
import java.util.List;

public record GetWeeklyAttendanceResult(
    DateRange range, List<LocalDate> attendedDates, int attendedCount) {
  public static GetWeeklyAttendanceResult of(
      LocalDate start, LocalDate end, List<LocalDate> attendedDates) {
    return new GetWeeklyAttendanceResult(
        new DateRange(start, end), attendedDates, attendedDates.size());
  }

  public record DateRange(LocalDate from, LocalDate to) {}
}
