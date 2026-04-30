package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.external.sanction;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.CheckTrainerSanctionUseCase;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that delegates trainer sanction checks to the sanction module.
 *
 * <p>Implements {@link CheckTrainerSanctionPort} (owned by the classes module) and calls {@link
 * CheckTrainerSanctionUseCase} (the sanction module's input port). This is the only place in the
 * classes module that is allowed to import from the sanction module, and it may only reference the
 * sanction module's {@code application/port/in/} layer.
 */
@Slf4j
@Component("classesTrainerSanctionCheckAdapter")
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
