package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GrantXpResultTest {

  @Test
  void granted_shouldComputeRemainingCount() {
    GrantXpResult result = GrantXpResult.granted(20, 5, 2, LocalDate.of(2026, 2, 28));

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.GRANTED);
    assertThat(result.grantedXp()).isEqualTo(20);
    assertThat(result.remainingCountToday()).isEqualTo(3);
  }

  @Test
  void alreadyGranted_shouldClampRemainingToZero() {
    GrantXpResult result = GrantXpResult.alreadyGranted(2, 10, LocalDate.of(2026, 2, 28));

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.ALREADY_GRANTED);
    assertThat(result.grantedXp()).isZero();
    assertThat(result.remainingCountToday()).isZero();
  }

  @Test
  void dailyCapReached_shouldUseMinusOneForUnlimitedCap() {
    GrantXpResult result = GrantXpResult.dailyCapReached(-1, 999, LocalDate.of(2026, 2, 28));

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.DAILY_CAP_REACHED);
    assertThat(result.remainingCountToday()).isEqualTo(-1);
  }
}
