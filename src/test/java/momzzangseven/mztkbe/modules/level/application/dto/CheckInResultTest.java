package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CheckInResultTest {

  @Test
  void alreadyCheckedIn_shouldSetFailureDefaults() {
    LocalDate date = LocalDate.of(2026, 2, 28);

    CheckInResult result = CheckInResult.alreadyCheckedIn(date);

    assertThat(result.success()).isFalse();
    assertThat(result.attendedDate()).isEqualTo(date);
    assertThat(result.grantedXp()).isZero();
    assertThat(result.bonusXp()).isZero();
    assertThat(result.streakDays()).isZero();
    assertThat(result.message()).isEqualTo("ALREADY_CHECKED_IN");
  }

  @Test
  void success_shouldSetGrantedValues() {
    LocalDate date = LocalDate.of(2026, 2, 28);

    CheckInResult result = CheckInResult.success(date, 10, 20, 7);

    assertThat(result.success()).isTrue();
    assertThat(result.attendedDate()).isEqualTo(date);
    assertThat(result.grantedXp()).isEqualTo(10);
    assertThat(result.bonusXp()).isEqualTo(20);
    assertThat(result.streakDays()).isEqualTo(7);
    assertThat(result.message()).isEqualTo("OK");
  }
}
