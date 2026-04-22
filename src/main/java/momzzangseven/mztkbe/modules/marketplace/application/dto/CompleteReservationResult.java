package momzzangseven.mztkbe.modules.marketplace.application.dto;

import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

/** Result returned after a user completes a reservation. */
public record CompleteReservationResult(Long reservationId, ReservationStatus status) {}
