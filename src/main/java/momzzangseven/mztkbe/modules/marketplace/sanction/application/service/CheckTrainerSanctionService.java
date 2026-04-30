package momzzangseven.mztkbe.modules.marketplace.sanction.application.service;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.CheckTrainerSanctionUseCase;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.LoadTrainerSanctionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case implementation for querying a trainer's active sanction status.
 *
 * <p>Delegates to {@link LoadTrainerSanctionPort} (implemented by {@code
 * SanctionPersistenceAdapter}) to perform the actual DB lookup. Cross-module callers (e.g. the
 * reservation module's {@code TrainerSanctionCheckAdapter}) must call this use case rather than the
 * output port directly, preserving the hexagonal dependency rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckTrainerSanctionService implements CheckTrainerSanctionUseCase {

  private final LoadTrainerSanctionPort loadTrainerSanctionPort;

  @Override
  @Transactional(readOnly = true)
  public boolean hasActiveSanction(Long trainerId) {
    boolean suspended = loadTrainerSanctionPort.hasActiveSanction(trainerId);
    log.debug("CheckTrainerSanction: trainerId={}, suspended={}", trainerId, suspended);
    return suspended;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LocalDateTime> getSuspendedUntil(Long trainerId) {
    return loadTrainerSanctionPort.getSuspendedUntil(trainerId);
  }
}
