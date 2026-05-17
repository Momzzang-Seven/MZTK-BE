package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CheckReservationExecutionCleanupProtectionUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCleanupProtectionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckReservationExecutionCleanupProtectionService
    implements CheckReservationExecutionCleanupProtectionUseCase {

  private final LoadReservationExecutionCleanupProtectionPort
      loadReservationExecutionCleanupProtectionPort;

  @Override
  @Transactional(readOnly = true)
  public List<String> findProtectedExecutionIntentPublicIds(
      List<ReservationExecutionCleanupProtectionQuery> intents) {
    return loadReservationExecutionCleanupProtectionPort.findProtectedExecutionIntentPublicIds(
        intents);
  }
}
