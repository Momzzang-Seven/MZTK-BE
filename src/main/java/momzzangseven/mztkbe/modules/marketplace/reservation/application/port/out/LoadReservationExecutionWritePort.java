package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;

/** Output port for owner-scoped executable marketplace execution intent reads. */
public interface LoadReservationExecutionWritePort {

  ReservationExecutionWriteView load(Long requesterUserId, String executionIntentId);
}
