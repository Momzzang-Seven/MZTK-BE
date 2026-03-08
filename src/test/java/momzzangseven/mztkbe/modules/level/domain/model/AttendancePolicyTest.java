package momzzangseven.mztkbe.modules.level.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttendancePolicyTest {

  @Test
  void calculateStreak_shouldReturnZeroWhenRecentDatesIsNull() {
    int streak = AttendancePolicy.calculateStreak(LocalDate.now(), null);

    assertThat(streak).isZero();
  }

  @Test
  void calculateStreak_shouldReturnZeroWhenCursorNotAttended() {
    LocalDate today = LocalDate.now();

    int streak = AttendancePolicy.calculateStreak(today, List.of(today.minusDays(1)));

    assertThat(streak).isZero();
  }

  @Test
  void calculateStreak_shouldCountConsecutiveDaysOnly() {
    LocalDate today = LocalDate.now();
    List<LocalDate> recentDates =
        List.of(
            today, today.minusDays(1), today.minusDays(1), today.minusDays(2), today.minusDays(4));

    int streak = AttendancePolicy.calculateStreak(today, recentDates);

    assertThat(streak).isEqualTo(3);
  }
}
