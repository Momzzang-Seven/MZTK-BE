package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class VerificationTimePolicyTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Test
  void todaySlotUsesInjectedClockAndZoneIdAtKstBoundary() {
    VerificationTimePolicy beforeMidnight =
        new VerificationTimePolicy(
            Clock.fixed(Instant.parse("2026-03-08T14:59:59Z"), ZoneOffset.UTC), KST);
    VerificationTimePolicy afterMidnight =
        new VerificationTimePolicy(
            Clock.fixed(Instant.parse("2026-03-08T15:00:00Z"), ZoneOffset.UTC), KST);

    assertThat(beforeMidnight.todayKst()).isEqualTo(LocalDate.of(2026, 3, 8));
    assertThat(afterMidnight.todayKst()).isEqualTo(LocalDate.of(2026, 3, 9));
  }

  @Test
  void slotDateIsDerivedFromStoredLocalCreatedAt() {
    VerificationTimePolicy policy = new VerificationTimePolicy(Clock.system(ZoneOffset.UTC), KST);
    LocalDateTime createdAt = LocalDateTime.of(2026, 3, 8, 23, 59, 59);

    assertThat(policy.slotDateOf(createdAt)).isEqualTo(LocalDate.of(2026, 3, 8));
    assertThat(policy.isSameSlotDate(createdAt, LocalDate.of(2026, 3, 8))).isTrue();
    assertThat(policy.isSameSlotDate(createdAt, LocalDate.of(2026, 3, 9))).isFalse();
  }

  @Test
  void slotRangeIsStartInclusiveAndEndExclusive() {
    VerificationTimePolicy policy = new VerificationTimePolicy(Clock.system(ZoneOffset.UTC), KST);

    VerificationTimePolicy.SlotRange range = policy.slotRange(LocalDate.of(2026, 3, 8));

    assertThat(range.start()).isEqualTo(LocalDateTime.of(2026, 3, 8, 0, 0));
    assertThat(range.endExclusive()).isEqualTo(LocalDateTime.of(2026, 3, 9, 0, 0));
  }
}
