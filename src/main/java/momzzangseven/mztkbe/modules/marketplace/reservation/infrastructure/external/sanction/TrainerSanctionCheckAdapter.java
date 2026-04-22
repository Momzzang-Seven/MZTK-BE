package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.sanction;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link CheckTrainerSanctionPort}.
 *
 * <p>The sanction module's check-sanction input port is not yet implemented. This stub always
 * reports no active sanction so that the reservation flow works end-to-end during development.
 *
 * <p><b>Active profiles:</b> {@code local}, {@code dev}, {@code test}. Excluded from {@code prod}.
 * Replace with a real adapter calling the sanction module's input port once available.
 */
@Component
@Profile({"local", "dev", "test"})
public class TrainerSanctionCheckAdapter implements CheckTrainerSanctionPort {

  @Override
  public boolean hasActiveSanction(Long trainerId) {
    // TODO: delegate to sanction module input port when available
    return false;
  }

  @Override
  public Optional<LocalDateTime> getSuspendedUntil(Long trainerId) {
    // TODO: delegate to sanction module input port when available
    return Optional.empty();
  }
}
