package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Output port for reservation-owned reads from the marketplace class module. */
public interface LoadReservationClassPort {

  Optional<ReservationClassSlotView> findSlotByIdWithLock(Long slotId);

  Optional<ReservationClassView> findClassById(Long classId);

  record ReservationClassSlotView(
      Long id,
      Long classId,
      List<DayOfWeek> daysOfWeek,
      LocalTime startTime,
      int capacity,
      boolean active) {

    public ReservationClassSlotView {
      daysOfWeek = daysOfWeek == null ? List.of() : List.copyOf(daysOfWeek);
    }
  }

  record ReservationClassView(
      Long id,
      Long trainerId,
      int priceAmount,
      int durationMinutes,
      String title,
      boolean active) {}
}
