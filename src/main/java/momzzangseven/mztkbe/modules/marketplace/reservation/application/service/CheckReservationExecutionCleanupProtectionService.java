package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CheckReservationExecutionCleanupProtectionUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCleanupProtectionPort;

public class CheckReservationExecutionCleanupProtectionService
    implements CheckReservationExecutionCleanupProtectionUseCase {

  private final LoadReservationExecutionCleanupProtectionPort
      loadReservationExecutionCleanupProtectionPort;

  public CheckReservationExecutionCleanupProtectionService(
      LoadReservationExecutionCleanupProtectionPort loadReservationExecutionCleanupProtectionPort) {
    this.loadReservationExecutionCleanupProtectionPort =
        loadReservationExecutionCleanupProtectionPort;
  }

  @Override
  public List<String> findProtectedExecutionIntentPublicIds(
      List<ReservationExecutionCleanupProtectionQuery> intents) {
    return loadReservationExecutionCleanupProtectionPort.findProtectedExecutionIntentPublicIds(
        intents);
  }
}
