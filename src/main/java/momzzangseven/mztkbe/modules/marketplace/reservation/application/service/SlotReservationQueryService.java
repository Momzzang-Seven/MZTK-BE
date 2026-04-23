package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetSlotReservationInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes slot-level reservation statistics to other modules via the input port {@link
 * GetSlotReservationInfoUseCase}.
 *
 * <p>The {@code classes} module's {@code SlotReservationAdapter} depends on this service (through
 * the use-case interface) rather than calling {@code LoadReservationPort} directly, ensuring the
 * dependency direction respects module boundaries.
 */
@Service
@RequiredArgsConstructor
public class SlotReservationQueryService implements GetSlotReservationInfoUseCase {

  private final LoadReservationPort loadReservationPort;

  @Override
  @Transactional(readOnly = true)
  public int countActiveReservations(Long slotId) {
    return loadReservationPort.countActiveReservationsBySlotId(slotId);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Map<Long, Integer> countActiveReservationsIn(java.util.List<Long> slotIds) {
    return loadReservationPort.countActiveReservationsBySlotIds(slotIds);
  }

  @Override
  @Transactional(readOnly = true)
  public int countActiveReservations(Long slotId, java.time.LocalDate date) {
    return loadReservationPort.countActiveReservationsBySlotIdAndDate(slotId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Map<java.time.LocalDate, Integer> countActiveReservationsForDateRange(
      Long slotId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
    return loadReservationPort.countActiveReservationsBySlotIdAndDateRange(
        slotId, startDate, endDate);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasAnyReservationHistory(Long slotId) {
    return loadReservationPort.hasAnyReservationHistory(slotId);
  }
}
