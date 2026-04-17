package momzzangseven.mztkbe.modules.marketplace.infrastructure.external.sanction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.CheckTrainerSanctionUseCase;
import org.springframework.stereotype.Component;

/**
 * Adapter connecting the {@code classes} module's {@link LoadTrainerSanctionPort} to the
 * {@code sanction} module's {@link CheckTrainerSanctionUseCase}.
 *
 * <p>ARCHITECTURE: Only {@code infrastructure/external/sanction/} may import the sanction module.
 * Application services must depend only on the output port interface.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainerSanctionAdapter implements LoadTrainerSanctionPort {

  // ARCHITECTURE: import only sanction/application/port/in — never sanction/infrastructure
  private final CheckTrainerSanctionUseCase checkTrainerSanctionUseCase;

  @Override
  public boolean hasActiveSanction(Long trainerId) {
    log.debug("Checking sanction status via sanction module for trainerId={}", trainerId);
    return checkTrainerSanctionUseCase.execute(trainerId).isSuspended();
  }
}
