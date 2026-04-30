package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Result returned after a trainer approves a reservation. */
public record ApproveReservationResult(Long reservationId, ReservationStatus status) {}
