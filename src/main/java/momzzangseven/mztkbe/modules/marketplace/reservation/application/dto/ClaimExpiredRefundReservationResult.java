package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Result returned after preparing a buyer deadline refund execution. */
public record ClaimExpiredRefundReservationResult(
    Long reservationId,
    ReservationDisplayStatus status,
    ReservationStatus businessStatus,
    String escrowStatus,
    ReservationExecutionWriteView web3) {}
