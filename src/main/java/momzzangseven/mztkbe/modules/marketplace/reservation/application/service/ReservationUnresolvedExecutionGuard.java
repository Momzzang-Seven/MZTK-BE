package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;

/** Blocks new user actions while another local or orphan marketplace execution is unresolved. */
final class ReservationUnresolvedExecutionGuard {

  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final ReservationExecutionCandidateGuard executionCandidateGuard;

  ReservationUnresolvedExecutionGuard(
      LoadReservationActionStatePort loadReservationActionStatePort,
      ReservationExecutionCandidateGuard executionCandidateGuard) {
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.executionCandidateGuard = executionCandidateGuard;
  }

  void requireNoUnresolvedExecution(Reservation reservation, String action) {
    if (loadReservationActionStatePort != null) {
      List<MarketplaceReservationActionState> activeActions =
          loadReservationActionStatePort.findByReservationIdAndStatuses(
              reservation.getId(),
              List.of(
                  ReservationActionStateStatus.PREPARING,
                  ReservationActionStateStatus.INTENT_BOUND));
      if (activeActions != null && !activeActions.isEmpty()) {
        throw conflict(action, "another marketplace action is already active for this reservation");
      }
    }
    if (executionCandidateGuard != null
        && executionCandidateGuard.hasBlockingExecutionForAnyMarketplaceAction(reservation)) {
      throw conflict(action, "another marketplace execution is unresolved for this reservation");
    }
  }

  private MarketplaceReservationStateException conflict(String action, String reason) {
    return new MarketplaceReservationStateException(
        ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
        "Cannot " + action + " reservation: " + reason);
  }
}
