package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ToggleClassStatusCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ToggleClassStatusResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.ToggleClassStatusUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for toggling the active/inactive status of a class.
 *
 * <p>When re-activating an inactive class, a suspension check is performed. Deactivation always
 * succeeds as long as ownership is confirmed.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ToggleClassStatusService implements ToggleClassStatusUseCase {

  private final LoadClassPort loadClassPort;
  private final SaveClassPort saveClassPort;
  private final LoadTrainerSanctionPort loadTrainerSanctionPort;

  @Override
  public ToggleClassStatusResult execute(ToggleClassStatusCommand command) {
    log.debug(
        "Toggling class status: classId={}, trainerId={}", command.classId(), command.trainerId());
    command.validate();

    MarketplaceClass marketplaceClass =
        loadClassPort
            .findById(command.classId())
            .orElseThrow(() -> new ClassNotFoundException(command.classId()));

    if (!marketplaceClass.isOwnedBy(command.trainerId())) {
      throw new MarketplaceUnauthorizedAccessException(command.classId(), command.trainerId());
    }

    // Check suspension only when activating an inactive class
    if (!marketplaceClass.isActive()
        && loadTrainerSanctionPort.hasActiveSanction(command.trainerId())) {
      throw new TrainerSuspendedException(command.trainerId());
    }

    MarketplaceClass toggled = marketplaceClass.toggleStatus();
    MarketplaceClass saved = saveClassPort.save(toggled);

    log.debug("Class status toggled: classId={}, active={}", saved.getId(), saved.isActive());
    return ToggleClassStatusResult.of(saved.getId(), saved.isActive());
  }
}
