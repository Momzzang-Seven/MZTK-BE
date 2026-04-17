package momzzangseven.mztkbe.modules.marketplace.infrastructure.stub;

import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadSlotReservationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link LoadSlotReservationPort}.
 *
 * <p>This adapter is active only while the reservation module is not yet wired into the Spring
 * context ({@code @ConditionalOnMissingBean}). It always returns zero reservations, allowing
 * UpdateClassService to proceed without the reservation module dependency.
 *
 * <p><b>Replace this stub</b> with a real adapter once the reservation module provides a
 * {@code ReservationRepository} or equivalent persistence port.
 */
@Component
@ConditionalOnMissingBean(
    name = "reservationSlotAdapter",
    value = LoadSlotReservationPort.class)
public class StubSlotReservationAdapter implements LoadSlotReservationPort {

  @Override
  public int countActiveReservations(Long slotId) {
    // Stub: reservation module not yet available — assumes 0 active reservations.
    return 0;
  }

  @Override
  public boolean hasActiveReservation(Long slotId) {
    // Stub: reservation module not yet available — assumes no active reservations.
    return false;
  }
}
