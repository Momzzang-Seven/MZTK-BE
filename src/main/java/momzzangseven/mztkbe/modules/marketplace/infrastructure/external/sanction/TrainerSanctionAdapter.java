package momzzangseven.mztkbe.modules.marketplace.infrastructure.external.sanction;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadTrainerSanctionPort;
import org.springframework.stereotype.Component;

/**
 * Stub adapter for the trainer sanction check.
 *
 * <p>The sanction module does not exist yet. This adapter always returns {@code false} (no active
 * sanction) so that the class registration and toggle flows work end-to-end without blocking.
 *
 * <p>When the sanction module is implemented, replace this component with a real adapter that calls
 * the sanction module's input port.
 */
@Slf4j
@Component
public class TrainerSanctionAdapter implements LoadTrainerSanctionPort {

  @Override
  public boolean hasActiveSanction(Long trainerId) {
    log.debug(
        "Sanction check stub — always returning false for trainerId={}. "
            + "Replace with real sanction module when available.",
        trainerId);
    return false;
  }
}
