package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.sanction;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.CheckTrainerSanctionUseCase;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that delegates trainer sanction checks to the sanction module.
 *
 * <p>Implements {@link CheckTrainerSanctionPort} (owned by the reservation module) and calls {@link
 * CheckTrainerSanctionUseCase} (the sanction module's input port). This is the only place in the
 * reservation module that is allowed to import from the sanction module, and it may only reference
 * the sanction module's {@code application/port/in/} layer.
 *
 * <p>Following the Cross-Module dependency rule from {@code ARCHITECTURE.md}:
 *
 * <pre>
 * reservation/application/service
 *         │  uses
 *         ▼
 * reservation/application/port/out/CheckTrainerSanctionPort
 *         │  implemented by
 *         ▼
 * reservation/infrastructure/external/sanction/TrainerSanctionCheckAdapter  ← this class
 *         │  calls
 *         ▼
 * sanction/application/port/in/CheckTrainerSanctionUseCase
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainerSanctionCheckAdapter implements CheckTrainerSanctionPort {

  private final CheckTrainerSanctionUseCase checkTrainerSanctionUseCase;

  @Override
  public boolean hasActiveSanction(Long trainerId) {
    return checkTrainerSanctionUseCase.hasActiveSanction(trainerId);
  }

  @Override
  public Optional<LocalDateTime> getSuspendedUntil(Long trainerId) {
    return checkTrainerSanctionUseCase.getSuspendedUntil(trainerId);
  }
}
