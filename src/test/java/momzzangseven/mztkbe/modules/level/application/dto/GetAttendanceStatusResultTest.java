package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GetAttendanceStatusResultTest {

  @Test
  void of_shouldReturnSameValues() {
    LocalDate today = LocalDate.of(2026, 2, 28);

    GetAttendanceStatusResult result = GetAttendanceStatusResult.of(today, true, 5);

    assertThat(result.today()).isEqualTo(today);
    assertThat(result.hasAttendedToday()).isTrue();
    assertThat(result.streakCount()).isEqualTo(5);
  }
}
