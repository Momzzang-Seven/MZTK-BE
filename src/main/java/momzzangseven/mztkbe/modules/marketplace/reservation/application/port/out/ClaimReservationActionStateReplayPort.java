package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;

public interface ClaimReservationActionStateReplayPort {

  List<MarketplaceReservationActionState> claimBoundAdminExecutionAttemptsForTerminalReplay(
      LocalDateTime claimStaleBefore, int batchSize);
}
