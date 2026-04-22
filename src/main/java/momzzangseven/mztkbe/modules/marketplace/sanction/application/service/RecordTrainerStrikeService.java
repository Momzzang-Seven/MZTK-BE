package momzzangseven.mztkbe.modules.marketplace.sanction.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.RecordTrainerStrikeUseCase;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.ManageTrainerSanctionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case implementation for recording a trainer strike.
 *
 * <p>This service is the single application-layer owner of the {@link ManageTrainerSanctionPort}
 * for strike operations. Infrastructure driving adapters (event listeners, schedulers) must call
 * this use case rather than the output port directly, preserving the hexagonal dependency rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordTrainerStrikeService implements RecordTrainerStrikeUseCase {

  private final ManageTrainerSanctionPort manageTrainerSanctionPort;

  @Override
  @Transactional
  public void execute(RecordTrainerStrikeCommand command) {
    log.debug(
        "RecordTrainerStrike: trainerId={}, reason={}", command.trainerId(), command.reason());

    ManageTrainerSanctionPort.RecordStrikeResult result =
        manageTrainerSanctionPort.recordStrike(command.trainerId(), command.reason());

    log.info(
        "Strike recorded: trainerId={}, reason={}, strikeCount={}, isBanned={}",
        command.trainerId(),
        command.reason(),
        result.strikeCount(),
        result.isBanned());
  }
}
