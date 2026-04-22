package momzzangseven.mztkbe.modules.marketplace.application.dto;

import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

/** Result returned after a trainer rejects a reservation. */
public record RejectReservationResult(Long reservationId, ReservationStatus status) {}
