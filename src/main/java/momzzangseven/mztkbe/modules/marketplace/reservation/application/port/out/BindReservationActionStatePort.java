package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;

public interface BindReservationActionStatePort {

  Optional<MarketplaceReservationActionState> bindExecutionIntent(
      Long actionStateId, String attemptToken, String executionIntentPublicId);
}
