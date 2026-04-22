package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.sanction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.RecordTrainerStrikeUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that bridges the reservation module's {@link RecordTrainerStrikePort} to the
 * sanction module's {@link RecordTrainerStrikeUseCase} input port.
 *
 * <p>This is the only class in the reservation module allowed to import from the sanction module,
 * per the ARCHITECTURE.md cross-module dependency rules.
 *
 * <p><b>Active profiles:</b> {@code local}, {@code dev}, {@code test}. The underlying {@code
 * ManageTrainerSanctionPort} implementation (sanction module) is also stub-only for these profiles.
 * Replace or promote to all profiles once the real sanction persistence is implemented.
 */
@Slf4j
@Component
@Profile({"local", "dev", "test", "integration"})
@RequiredArgsConstructor
public class TrainerStrikeRecordAdapter implements RecordTrainerStrikePort {

  private final RecordTrainerStrikeUseCase recordTrainerStrikeUseCase;

  @Override
  public void recordStrike(Long trainerId, String reason) {
    log.debug("RecordTrainerStrike via adapter: trainerId={}, reason={}", trainerId, reason);
    recordTrainerStrikeUseCase.execute(new RecordTrainerStrikeCommand(trainerId, reason));
  }
}
