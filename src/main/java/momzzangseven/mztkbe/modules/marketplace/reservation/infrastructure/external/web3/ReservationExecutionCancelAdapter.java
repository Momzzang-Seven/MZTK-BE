package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reservation-owned adapter that bridges Phase B compensation to shared execution cancellation. */
@Component
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ReservationExecutionCancelAdapter implements CancelReservationEscrowExecutionPort {

  private final CancelExecutionIntentUseCase cancelExecutionIntentUseCase;

  @Override
  public boolean cancelSignableIntent(
      String executionIntentId, String errorCode, String errorReason) {
    return cancelExecutionIntentUseCase.cancelIfSignable(
        new CancelExecutionIntentCommand(executionIntentId, errorCode, errorReason));
  }
}
