package momzzangseven.mztkbe.modules.verification.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/** KST-based slot date policy used by workout verification flows. */
@Component
public class VerificationTimePolicy {

  private final Clock appClock;
  private final ZoneId appZoneId;

  public VerificationTimePolicy(Clock appClock, ZoneId appZoneId) {
    this.appClock = appClock;
    this.appZoneId = appZoneId;
  }

  public LocalDate todayKst() {
    return ZonedDateTime.now(appClock.withZone(appZoneId)).toLocalDate();
  }

  public LocalDate slotDateOf(LocalDateTime createdAt) {
    return createdAt.toLocalDate();
  }

  public boolean isSameSlotDate(LocalDateTime createdAt, LocalDate slotDate) {
    return slotDateOf(createdAt).isEqual(slotDate);
  }

  public SlotRange slotRange(LocalDate slotDate) {
    LocalDateTime start = slotDate.atStartOfDay();
    return new SlotRange(start, start.plusDays(1));
  }

  /** Inclusive start / exclusive end range for a verification slot date. */
  public record SlotRange(LocalDateTime start, LocalDateTime endExclusive) {}
}
