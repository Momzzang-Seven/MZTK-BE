package momzzangseven.mztkbe.modules.marketplace.infrastructure.external.reservation;

import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadSlotReservationPort;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link LoadSlotReservationPort}.
 *
 * <p>The reservation module is not yet integrated with the marketplace class module. This adapter
 * always reports zero active reservations and no reservation history, allowing all slot
 * modifications (including hard-deletes) to proceed without restriction.
 *
 * <p>Replace this stub with a real cross-module adapter once the reservation module is available.
 * The real adapter should call the reservation module's input port (e.g. {@code
 * GetSlotReservationCountUseCase}) — never the reservation module's infrastructure directly.
 */
@Component
public class SlotReservationAdapter implements LoadSlotReservationPort {

  @Override
  public int countActiveReservations(Long slotId) {
    // TODO: delegate to reservation module input port when available
    return 0;
  }

  @Override
  public boolean hasAnyReservationHistory(Long slotId) {
    // TODO: delegate to reservation module input port when available
    return false;
  }
}
