package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayTerminatedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconcileMarketplaceAdminTerminalExecutionAttemptServiceTest {

  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private ReplayTerminatedReservationExecutionPort replayTerminatedReservationExecutionPort;

  @Test
  void execute_replaysBoundAdminTerminalHookCandidates() {
    var service =
        new ReconcileMarketplaceAdminTerminalExecutionAttemptService(
            loadReservationActionStatePort, replayTerminatedReservationExecutionPort);
    given(loadReservationActionStatePort.findBoundAdminExecutionAttemptsForTerminalReplay(10))
        .willReturn(List.of(actionState(1L, ReservationEscrowAction.ADMIN_REFUND, "intent-1")));
    given(
            replayTerminatedReservationExecutionPort.replayTerminated(
                "intent-1", "MARKETPLACE_ADMIN_REFUND"))
        .willReturn(true);

    var result = service.execute(new ReconcileMarketplaceAdminTerminalExecutionAttemptCommand(10));

    assertThat(result.scanned()).isEqualTo(1);
    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.skipped()).isZero();
    assertThat(result.failed()).isZero();
    verify(replayTerminatedReservationExecutionPort)
        .replayTerminated("intent-1", "MARKETPLACE_ADMIN_REFUND");
  }

  private MarketplaceReservationActionState actionState(
      Long id, ReservationEscrowAction action, String executionIntentId) {
    return MarketplaceReservationActionState.builder()
        .id(id)
        .reservationId(77L)
        .escrowId(88L)
        .actionType(action)
        .attemptToken("attempt-" + id)
        .executionIntentPublicId(executionIntentId)
        .status(ReservationActionStateStatus.INTENT_BOUND)
        .build();
  }
}
