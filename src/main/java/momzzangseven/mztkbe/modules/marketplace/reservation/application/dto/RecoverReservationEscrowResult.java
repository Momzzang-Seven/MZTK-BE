package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Result returned after resuming or preparing a marketplace recovery execution. */
public record RecoverReservationEscrowResult(
    Long reservationId,
    ReservationDisplayStatus status,
    ReservationStatus businessStatus,
    String escrowStatus,
    ReservationExecutionWriteView web3) {}
