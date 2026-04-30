package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Result returned after a trainer rejects a reservation. */
public record RejectReservationResult(Long reservationId, ReservationStatus status) {}
