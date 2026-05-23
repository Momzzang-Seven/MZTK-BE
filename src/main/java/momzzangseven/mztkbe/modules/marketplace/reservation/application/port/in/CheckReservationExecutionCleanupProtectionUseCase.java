package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;

/** Checks reservation-owned references that protect finalized execution intents from cleanup. */
public interface CheckReservationExecutionCleanupProtectionUseCase {

  List<String> findProtectedExecutionIntentPublicIds(
      List<ReservationExecutionCleanupProtectionQuery> intents);
}
