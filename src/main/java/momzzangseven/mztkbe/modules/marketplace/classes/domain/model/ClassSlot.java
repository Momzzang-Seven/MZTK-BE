package momzzangseven.mztkbe.modules.marketplace.classes.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCapacityException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidSlotException;

/**
 * Domain model representing a single time-slot for a marketplace class.
 *
 * <p>Each slot defines which days of the week the class runs, the start time, and the maximum
 * participant capacity. The end time is always derived on-the-fly from {@code
 * startTime.plusMinutes(durationMinutes)} to avoid data drift.
 *
 * <h2>Midnight-crossing conflict detection</h2>
 *
 * <p>A session that starts e.g. at 23:00 with 120-minute duration crosses midnight into the next
 * day. To handle this correctly we use the "weekly cumulative minutes" (WCM) scheme:
 *
 * <ul>
 *   <li>Monday 00:00 → WCM = 0
 *   <li>Sunday 23:59 → WCM = 10079
 *   <li>A slot on day D starting at H:M occupies the WCM range {@code [D*1440 + H*60 + M, D*1440 +
 *       H*60 + M + durationMinutes)}.
 * </ul>
 *
 * <p>When the upper bound of that range exceeds {@code 10080} (a full week), the session wraps into
 * the next week's Monday. We canonicalise this with modular arithmetic: we split the interval into
 * at most two non-wrapping segments and test for overlap against all segments of the other slot.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClassSlot {

  // ============================================
  // Constants
  // ============================================

  /** Total number of minutes in one week (7 × 24 × 60). */
  static final int MINUTES_PER_WEEK = 7 * 24 * 60; // 10080

  // ============================================
  // Fields
  // ============================================

  private final Long id;
  private final Long classId;
  private final List<DayOfWeek> daysOfWeek;
  private final LocalTime startTime;
  private final int capacity;
  private final boolean active;

  // ============================================
  // Factory Methods
  // ============================================

  /**
   * Create a new ClassSlot.
   *
   * @param classId owning class ID
   * @param daysOfWeek list of days this slot runs on
   * @param startTime slot start time
   * @param capacity max participants (must be >= 1)
   * @return new active ClassSlot instance
   */
  public static ClassSlot create(
      Long classId, List<DayOfWeek> daysOfWeek, LocalTime startTime, int capacity) {

    validateClassId(classId);
    validateDaysOfWeek(daysOfWeek);
    validateStartTime(startTime);
    validateCapacity(capacity);

    return ClassSlot.builder()
        .classId(classId)
        .daysOfWeek(daysOfWeek == null ? List.of() : List.copyOf(daysOfWeek))
        .startTime(startTime)
        .capacity(capacity)
        .active(true)
        .build();
  }

  /**
   * Update this slot's mutable fields while preserving identity (id, classId).
   *
   * @param daysOfWeek new days of week
   * @param startTime new start time
   * @param capacity new capacity
   * @return new ClassSlot instance with updated values
   */
  public ClassSlot update(List<DayOfWeek> daysOfWeek, LocalTime startTime, int capacity) {
    validateDaysOfWeek(daysOfWeek);
    validateStartTime(startTime);
    validateCapacity(capacity);

    return toBuilder()
        .daysOfWeek(daysOfWeek == null ? List.of() : List.copyOf(daysOfWeek))
        .startTime(startTime)
        .capacity(capacity)
        .build();
  }

  /**
   * Soft-delete this slot by marking it inactive. The row is retained for reservation history.
   *
   * @return new ClassSlot instance with active=false
   */
  public ClassSlot softDelete() {
    return toBuilder().active(false).build();
  }

  // ============================================
  // Domain Query Methods
  // ============================================

  /**
   * Returns the "weekly cumulative minutes" (WCM) for a specific day-of-week and this slot's start
   * time.
   *
   * <p>Monday = 0, Sunday = 6 → WCM range is {@code [0, 10079]}.
   *
   * @param day the day of week
   * @return WCM value (0-based from Monday 00:00)
   */
  public int weeklyMinutes(DayOfWeek day) {
    int dayOffset = day.getValue() - 1; // Monday=0, Sunday=6
    return dayOffset * 24 * 60 + startTime.getHour() * 60 + startTime.getMinute();
  }

  /**
   * Returns true if this slot's time interval conflicts with {@code other} on any shared day of
   * week, handling midnight-crossing sessions correctly.
   *
   * <h3>Algorithm</h3>
   *
   * <p>Each slot {@code A} on day D produces a half-open interval {@code [startA, startA +
   * duration)} in weekly-minutes space (0–10079 + wrap). When {@code startA + duration >
   * MINUTES_PER_WEEK} the interval wraps around the week boundary.
   *
   * <p>We represent every interval as one or two non-wrapping segments:
   *
   * <pre>
   * No wrap:  one segment  [s, s+d)
   * Wrap:     two segments [s, WEEK) and [0, (s+d) % WEEK)
   * </pre>
   *
   * <p>Two interval sets conflict when any segment of A overlaps any segment of B. Two segments
   * {@code [sA, eA)} and {@code [sB, eB)} overlap iff {@code sA < eB && sB < eA}.
   *
   * @param other the other slot to test against
   * @param durationMinutes session duration shared by all slots of the same class
   * @return true if the intervals overlap on any common day
   */
  public boolean conflictsWith(ClassSlot other, int durationMinutes) {
    List<int[]> segmentsA = new java.util.ArrayList<>();
    for (DayOfWeek day : this.daysOfWeek) {
      int start = weeklyMinutes(day);
      segmentsA.addAll(toSegments(start, start + durationMinutes));
    }

    List<int[]> segmentsB = new java.util.ArrayList<>();
    for (DayOfWeek day : other.getDaysOfWeek()) {
      int start = other.weeklyMinutes(day);
      segmentsB.addAll(toSegments(start, start + durationMinutes));
    }

    for (int[] segA : segmentsA) {
      for (int[] segB : segmentsB) {
        if (segA[0] < segB[1] && segB[0] < segA[1]) {
          return true;
        }
      }
    }
    return false;
  }

  // ============================================
  // Validation Methods
  // ============================================

  private static void validateClassId(Long classId) {
    if (classId == null || classId <= 0) {
      throw new MarketplaceInvalidSlotException("Class ID must be a positive number");
    }
  }

  private static void validateDaysOfWeek(List<DayOfWeek> daysOfWeek) {
    if (daysOfWeek == null || daysOfWeek.isEmpty()) {
      throw new MarketplaceInvalidSlotException("Days of week must not be null or empty");
    }
    // A slot with duplicate days would double-count intervals in conflict detection and
    // produce confusing schedule entries (e.g., two Monday sessions in one slot).
    long distinctCount = daysOfWeek.stream().distinct().count();
    if (distinctCount < daysOfWeek.size()) {
      throw new MarketplaceInvalidSlotException("Days of week must not contain duplicate values");
    }
  }

  private static void validateStartTime(LocalTime startTime) {
    if (startTime == null) {
      throw new MarketplaceInvalidSlotException("Start time must not be null");
    }
  }

  private static void validateCapacity(int capacity) {
    if (capacity < 1) {
      throw new MarketplaceInvalidCapacityException(
          "Capacity must be at least 1, got: " + capacity);
    }
  }

  // ============================================
  // Private helpers
  // ============================================

  /**
   * Decomposes a half-open interval {@code [start, end)} in weekly-minutes space into one or two
   * non-wrapping segments.
   *
   * <pre>
   * No wrap  (end <= MINUTES_PER_WEEK):  → [[start, end)]
   * Wrap     (end >  MINUTES_PER_WEEK):  → [[start, MINUTES_PER_WEEK), [0, end % MINUTES_PER_WEEK)]
   * </pre>
   *
   * <p>This ensures that a Friday-23:00 session with 120 min is correctly represented as two
   * segments: {@code [Friday 23:00, Saturday 00:00)} and {@code [Monday 00:00, Monday 01:00)},
   * where "Monday" here means the virtual "next week Monday" at WCM offset 0.
   *
   * <p>Note: The second segment wraps into the next week's Monday–Sunday space. Since all slots are
   * evaluated in the same 10080-minute window, a wrap on Sunday night vs a slot on early Monday
   * morning is correctly detected.
   *
   * @param start WCM start (0 ≤ start &lt; MINUTES_PER_WEEK)
   * @param end WCM end = start + durationMinutes (may exceed MINUTES_PER_WEEK)
   * @return list of one or two {@code [inclusive-start, exclusive-end]} arrays
   */
  private static List<int[]> toSegments(int start, int end) {
    if (end <= MINUTES_PER_WEEK) {
      // Simple non-wrapping interval
      return List.of(new int[] {start, end});
    } else {
      // Midnight-wrap: split into [start, WEEK) and [0, overflow)
      int overflow = end - MINUTES_PER_WEEK;
      return List.of(new int[] {start, MINUTES_PER_WEEK}, new int[] {0, overflow});
    }
  }

  // ============================================
  // Getters (override for unmodifiable list)
  // ============================================

  /**
   * Returns an unmodifiable view of the days-of-week list. Never null.
   *
   * @return unmodifiable list of days
   */
  public List<DayOfWeek> getDaysOfWeek() {
    return daysOfWeek == null ? List.of() : Collections.unmodifiableList(daysOfWeek);
  }

  /** Lombok builder supplement for safe list initialization. */
  public static class ClassSlotBuilder {
    private List<DayOfWeek> daysOfWeek = new ArrayList<>();
  }
}
