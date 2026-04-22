package momzzangseven.mztkbe.modules.marketplace.application.dto;

import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

/** Result returned after a trainer approves a reservation. */
public record ApproveReservationResult(Long reservationId, ReservationStatus status) {}
