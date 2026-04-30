package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.external.reservation;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadSlotReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetSlotReservationInfoUseCase;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link LoadSlotReservationPort} backed by {@link
 * GetSlotReservationInfoUseCase}.
 *
 * <p>Bridges the classes module's slot-management needs to the reservation module using the correct
 * cross-module pattern: depending on the reservation module's <em>input port</em> (use-case
 * interface) rather than its internal output port.
 */
@Component
@RequiredArgsConstructor
public class SlotReservationAdapter implements LoadSlotReservationPort {

  private final GetSlotReservationInfoUseCase getSlotReservationInfoUseCase;

  @Override
  public int countActiveReservations(Long slotId) {
    return getSlotReservationInfoUseCase.countActiveReservations(slotId);
  }

  @Override
  public java.util.Map<Long, Integer> countActiveReservationsIn(java.util.List<Long> slotIds) {
    return getSlotReservationInfoUseCase.countActiveReservationsIn(slotIds);
  }

  @Override
  public int countActiveReservations(Long slotId, java.time.LocalDate date) {
    return getSlotReservationInfoUseCase.countActiveReservations(slotId, date);
  }

  @Override
  public java.util.Map<java.time.LocalDate, Integer> countActiveReservationsForDateRange(
      Long slotId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
    return getSlotReservationInfoUseCase.countActiveReservationsForDateRange(
        slotId, startDate, endDate);
  }

  @Override
  public boolean hasAnyReservationHistory(Long slotId) {
    return getSlotReservationInfoUseCase.hasAnyReservationHistory(slotId);
  }
}
