package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetWeeklyAttendanceResultTest {

  @Test
  void of_shouldCreateDateRangeAndCount() {
    LocalDate start = LocalDate.of(2026, 2, 22);
    LocalDate end = LocalDate.of(2026, 2, 28);
    List<LocalDate> attended = List.of(end.minusDays(1), end);

    GetWeeklyAttendanceResult result = GetWeeklyAttendanceResult.of(start, end, attended);

    assertThat(result.range().from()).isEqualTo(start);
    assertThat(result.range().to()).isEqualTo(end);
    assertThat(result.attendedDates()).containsExactlyElementsOf(attended);
    assertThat(result.attendedCount()).isEqualTo(2);
  }
}
