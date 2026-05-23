package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Slot fields required by the reservation module while holding the slot/date capacity lock.
 *
 * @param slotId primary key
 * @param classId owning class ID
 * @param daysOfWeek reservable days for the slot
 * @param startTime slot start time
 * @param capacity slot capacity
 * @param active whether the slot is currently reservable
 */
public record ClassSlotReservationProjection(
    Long slotId,
    Long classId,
    List<DayOfWeek> daysOfWeek,
    LocalTime startTime,
    int capacity,
    boolean active) {

  public ClassSlotReservationProjection {
    daysOfWeek = daysOfWeek == null ? List.of() : List.copyOf(daysOfWeek);
  }
}
