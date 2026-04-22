package momzzangseven.mztkbe.modules.marketplace.application.dto;

import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

/** Result returned after a user cancels a pending reservation. */
public record CancelPendingReservationResult(Long reservationId, ReservationStatus status) {}
