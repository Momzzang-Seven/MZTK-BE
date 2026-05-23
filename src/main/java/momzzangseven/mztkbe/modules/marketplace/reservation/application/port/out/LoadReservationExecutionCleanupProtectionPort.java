package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;

/** Loads reservation-local evidence that keeps execution intents recoverable. */
public interface LoadReservationExecutionCleanupProtectionPort {

  List<String> findProtectedExecutionIntentPublicIds(
      List<ReservationExecutionCleanupProtectionQuery> intents);
}
