package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;

/** Output port for owner-agnostic execution state reads used by recovery guards. */
public interface LoadReservationExecutionStatePort {

  ReservationExecutionStateView loadState(String executionIntentId);
}
