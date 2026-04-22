package momzzangseven.mztkbe.modules.marketplace.infrastructure.external.reservation;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadSlotReservationPort;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link LoadSlotReservationPort} backed by {@link LoadReservationPort}.
 *
 * <p>Bridges the class module's slot-management needs to the reservation persistence layer using
 * the proper cross-module port pattern.
 */
@Component
@RequiredArgsConstructor
public class SlotReservationAdapter implements LoadSlotReservationPort {

  private final LoadReservationPort loadReservationPort;

  @Override
  public int countActiveReservations(Long slotId) {
    return loadReservationPort.countActiveReservationsBySlotId(slotId);
  }

  @Override
  public boolean hasAnyReservationHistory(Long slotId) {
    return loadReservationPort.hasAnyReservationHistory(slotId);
  }
}
