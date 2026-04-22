package momzzangseven.mztkbe.modules.marketplace.infrastructure.external.sanction;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadTrainerSanctionPort;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link LoadTrainerSanctionPort}.
 *
 * <p>The sanction module is not yet implemented. This adapter always reports no active sanction
 * ({@code hasActiveSanction = false}) and no suspension end time ({@code getSuspendedUntil =
 * empty}) so that the marketplace class flow compiles and runs end-to-end. Replace with a real
 * cross-module adapter once the sanction module is available.
 */
@Component
public class SanctionCheckAdapter implements LoadTrainerSanctionPort {

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
