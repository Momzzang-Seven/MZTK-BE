package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationTimePolicyTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Test
  @DisplayName("source_ref prefix로 completed method를 파생한다")
  void deriveCompletedMethod() {
    VerificationTimePolicy policy =
        new VerificationTimePolicy(KST, Clock.fixed(Instant.parse("2026-03-12T15:00:00Z"), KST));

    assertThat(policy.deriveCompletedMethod("location-verification:1").name())
        .isEqualTo("LOCATION");
    assertThat(policy.deriveCompletedMethod("workout-photo-verification:1").name())
        .isEqualTo("WORKOUT_PHOTO");
    assertThat(policy.deriveCompletedMethod("workout-record-verification:1").name())
        .isEqualTo("WORKOUT_RECORD");
    assertThat(policy.deriveCompletedMethod("unknown:1").name()).isEqualTo("UNKNOWN");
  }

  @Test
  @DisplayName("KST 기준 today 판정을 수행한다")
  void isTodayInKst() {
    VerificationTimePolicy policy =
        new VerificationTimePolicy(KST, Clock.fixed(Instant.parse("2026-03-12T15:00:00Z"), KST));

    assertThat(policy.today()).isEqualTo(LocalDate.of(2026, 3, 13));
    assertThat(policy.isToday(Instant.parse("2026-03-13T01:00:00Z"))).isTrue();
    assertThat(policy.isToday(Instant.parse("2026-03-11T14:59:59Z"))).isFalse();
    assertThat(policy.isToday(LocalDate.of(2026, 3, 13))).isTrue();
    assertThat(policy.isToday((LocalDate) null)).isFalse();
  }

  @Test
  @DisplayName("blank/null source_ref는 UNKNOWN으로 처리한다")
  void deriveCompletedMethodForBlankOrNullSourceRef() {
    VerificationTimePolicy policy =
        new VerificationTimePolicy(KST, Clock.fixed(Instant.parse("2026-03-12T15:00:00Z"), KST));

    assertThat(policy.deriveCompletedMethod(null).name()).isEqualTo("UNKNOWN");
    assertThat(policy.deriveCompletedMethod(" ").name()).isEqualTo("UNKNOWN");
  }
}
