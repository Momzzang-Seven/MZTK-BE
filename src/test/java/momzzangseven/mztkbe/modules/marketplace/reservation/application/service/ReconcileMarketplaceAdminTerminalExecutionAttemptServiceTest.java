package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
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
  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  @Mock private ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  @Mock private ReplayTerminatedReservationExecutionPort replayTerminatedReservationExecutionPort;

  @Test
  void execute_replaysBoundAdminTerminalHookCandidates() {
    var service = service();
    given(loadReservationActionStatePort.findBoundAdminExecutionAttemptsForTerminalReplay(10))
        .willReturn(List.of(actionState(1L, ReservationEscrowAction.ADMIN_REFUND, "intent-1")));
    given(loadReservationExecutionStatePort.loadState("intent-1"))
        .willReturn(
            new ReservationExecutionStateView(
                "intent-1", "FAILED_ONCHAIN", "MARKETPLACE_ADMIN_REFUND", 100L));
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

  @Test
  void execute_replaysConfirmedAdminHookCandidates() {
    var service = service();
    given(loadReservationActionStatePort.findBoundAdminExecutionAttemptsForTerminalReplay(10))
        .willReturn(List.of(actionState(2L, ReservationEscrowAction.ADMIN_SETTLE, "intent-2")));
    given(loadReservationExecutionStatePort.loadState("intent-2"))
        .willReturn(
            new ReservationExecutionStateView(
                "intent-2", "CONFIRMED", "MARKETPLACE_ADMIN_SETTLE", 100L));
    given(
            replayConfirmedReservationExecutionPort.replayConfirmed(
                "intent-2", "MARKETPLACE_ADMIN_SETTLE"))
        .willReturn(true);

    var result = service.execute(new ReconcileMarketplaceAdminTerminalExecutionAttemptCommand(10));

    assertThat(result.scanned()).isEqualTo(1);
    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.skipped()).isZero();
    assertThat(result.failed()).isZero();
    verify(replayConfirmedReservationExecutionPort)
        .replayConfirmed("intent-2", "MARKETPLACE_ADMIN_SETTLE");
  }

  @Test
  void execute_skipsNonTerminalExecutionState() {
    var service = service();
    given(loadReservationActionStatePort.findBoundAdminExecutionAttemptsForTerminalReplay(10))
        .willReturn(List.of(actionState(3L, ReservationEscrowAction.ADMIN_REFUND, "intent-3")));
    given(loadReservationExecutionStatePort.loadState("intent-3"))
        .willReturn(
            new ReservationExecutionStateView(
                "intent-3", "SUBMITTED", "MARKETPLACE_ADMIN_REFUND", 100L));

    var result = service.execute(new ReconcileMarketplaceAdminTerminalExecutionAttemptCommand(10));

    assertThat(result.scanned()).isEqualTo(1);
    assertThat(result.replayed()).isZero();
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.failed()).isZero();
  }

  private ReconcileMarketplaceAdminTerminalExecutionAttemptService service() {
    return new ReconcileMarketplaceAdminTerminalExecutionAttemptService(
        loadReservationActionStatePort,
        loadReservationExecutionStatePort,
        replayConfirmedReservationExecutionPort,
        replayTerminatedReservationExecutionPort);
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
