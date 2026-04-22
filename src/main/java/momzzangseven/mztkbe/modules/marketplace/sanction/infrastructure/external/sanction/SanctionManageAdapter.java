package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.external.sanction;

import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.ManageTrainerSanctionPort;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link ManageTrainerSanctionPort}.
 *
 * <p>Replace with a real adapter calling the sanction module's RecordTrainerStrikeUseCase input
 * port once the sanction module is available.
 */
@Slf4j
@Component
public class SanctionManageAdapter implements ManageTrainerSanctionPort {

  @Override
  public RecordStrikeResult recordStrike(Long trainerId, String reason) {
    // TODO: delegate to sanction module RecordTrainerStrikeUseCase once available
    log.warn("[STUB] recordStrike: trainerId={}, reason={}", trainerId, reason);
    return new RecordStrikeResult(1, false);
  }
}
